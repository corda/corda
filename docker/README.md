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
> Cannot convert string value 'NON_EXISTENT_IMAGE_VARIANT' to an enum value of type 'ImageVariant' (valid case insensitive values: UBUNTU_ZULU, UBUNTU_ZULU_11, AL_CORRETTO, ARM, OFFICIAL)
```
If no image variant is specified, all available image variants will be built.

The default repository for all images is `corda/corda` and you will need official R3 credentials 
for Artifactory to push there. R3's Artifactory administrators can assist with this if needed,
otherwise you can override the repository name using the `docker.image.repository` Gradle property.

e.g.
```
gradlew docker:pushDockerImage -Pdocker.image.repository=MyOwnRepository/test --image OFFICIAL --registry-url registry.hub.docker.com
```


