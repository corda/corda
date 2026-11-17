# Building docker images

There are 3 Gradle tasks dedicated to this:

- `buildDockerFolder` will simply create the build folder with all the dockerfiles and the required
artifacts (like the `corda.jar`, `config-exporter.jar`). 
This is an internal task and doesn't need to be explicitly invoked. 
- `buildDockerImage` will build a docker image a publish it in the local docker cache
- `pushDockerImage` will build a docker image and push it to a remote docker registry. 
It is possible to override the docker registry URL for all images using the `--registry-url` 
command-line parameter, otherwise each image will be pushed to its own preconfigured docker registry 
as specified in the `net.corda.build.docker.DockerImage.destination` field. 
The latter field is currently left as `null` for all image variants, which means they are pushed to
 [docker hub](https://hub.docker.com).

All 3 tasks use the command-line parameter `--image` to specify which image variant will be built 
(it can be used multiple times to build multiple images). 
To get a list of all supported variants simply launch with a random string:
```
gradlew docker:buildDockerImage --image NON_EXISTENT_IMAGE_VARIANT
```
results in
```
> Cannot convert string value 'NON_EXISTENT_IMAGE_VARIANT' to an enum value of type 'ImageVariant' (valid case insensitive values: UBUNTU_ZULU, UBUNTU_ZULU_11, AL_CORRETTO, OFFICIAL)
```
If no image variant is specified, all available image variants will be built.

The default repository for all images is `corda/corda` and you will need official R3 credentials 
for Artifactory to push there. R3's Artifactory administrators can assist with this if needed,
otherwise you can override the repository name using the `docker.image.repository` Gradle property.

e.g.
```
gradlew docker:pushDockerImage -Pdocker.image.repository=MyOwnRepository/test --image OFFICIAL --registry-url registry.hub.docker.com
```

# Running the image

## Volumes

The image declares these folders as volumes, so they are listed by `docker inspect` and survive container
restarts even when nothing is mounted:

| Folder | Contents | Written by the node | Production recommendation |
|--------|----------|---------------------|---------------------------|
| `/opt/corda/persistence` | H2 database | Yes | Writable named volume / PVC |
| `/opt/corda/artemis` | Message journal | Yes | Writable named volume / PVC |
| `/opt/corda/certificates` | Node keystores | Yes | Writable named volume / PVC |
| `/opt/corda/logs` | Log files | Yes | Writable volume, or ship logs elsewhere |
| `/opt/corda/additional-node-infos` | Node info files | Yes: the node writes its own node info here on every start | Writable |
| `/opt/corda/cordapps` | CorDapp jars, and CorDapp config in `cordapps/config` | Only creates `cordapps/config` if it is missing | Read-only (`:ro`), provided the mounted folder already contains a `config` subfolder (it may be empty); otherwise writable |
| `/opt/corda/drivers` | JDBC driver jars | No | Read-only (`:ro`) |
| `/etc/corda` | `node.conf` | Only by `config-generator` | Read-only (`:ro`) after the configuration is generated |

`persistence`, `artemis` and `certificates` have mode `750`, so only the `corda` user and group can read them.
The node runs as `corda` (UID 1000): folders mounted read-write from the host must be writable by that UID.

Nothing is enforced by the image: for evaluation and development you can mount host folders read-write, or
copy CorDapps in with `docker cp`, as before. For production, mounting CorDapps, drivers and configuration
read-only means code and configuration loaded by the node cannot be changed from inside the container.

```
mkdir -p /path/to/cordapps/config   # required for the read-only cordapps mount

docker run -d --name corda-node \
  -v corda-persistence:/opt/corda/persistence \
  -v corda-artemis:/opt/corda/artemis \
  -v corda-certificates:/opt/corda/certificates \
  -v corda-logs:/opt/corda/logs \
  -v corda-node-infos:/opt/corda/additional-node-infos \
  -v /path/to/config:/etc/corda:ro \
  -v /path/to/cordapps:/opt/corda/cordapps:ro \
  -v /path/to/drivers:/opt/corda/drivers:ro \
  corda/open-source:<version>-zulu-openjdk
```

## Health check

The image reports healthy once the node is listening on its RPC port (`docker ps` shows `(healthy)`). The
check allows 5 minutes for startup and then runs every 30 seconds. It uses `MY_RPC_PORT` (default `10201`):
if `rpcSettings.address` in `node.conf` uses a different port, set `CORDA_HEALTHCHECK_PORT` to that port,
otherwise the container is reported `unhealthy`. This matters under Docker Swarm, which restarts unhealthy
containers: set `CORDA_HEALTHCHECK_PORT`, or disable the check with `--no-healthcheck`.

Kubernetes ignores the Docker health check. Call `/opt/corda/bin/healthcheck` from an `exec` probe, or use a
`tcpSocket` probe on the RPC port.

## Runtime hardening

The image runs as the non-root `corda` user on unprivileged ports and supports `--cap-drop ALL` and
`--security-opt no-new-privileges` (Kubernetes: `capabilities.drop: ["ALL"]`,
`allowPrivilegeEscalation: false`). The image runs as UID/GID `1000:1000`, so Kubernetes
`runAsNonRoot: true` works without setting `runAsUser`.

Known limitation: a read-only root filesystem (`--read-only`, Kubernetes `readOnlyRootFilesystem: true`) is
not supported yet. The node writes runtime files (`network-parameters`, `nodeInfo-*`, `process-id`) to its
base directory `/opt/corda`, which also contains the Corda binaries. If you mount a tmpfs or `emptyDir` on
`/tmp`, it must allow executing files: BouncyCastle and Jansi extract native libraries there.
