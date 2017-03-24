SGX build dependencies
===

The aim of this folder is to pin some build dependencies of the SGX work that would otherwise cause reproducibility problems.

Normally you shouldn't care about this folder, everything should work as long as you use the Makefile in the parent folder.

The pinned dependencies reside in `root/`.

If dependencies need to be changed/upgraded
---

There are two dockerfiles, one in `docker-full` specifying all build dependencies, and one in `docker-minimal`, specifying the minimal system we assume developers have.

Once we build `docker-full` we can extract the relevant parts of the filesystem using `extract_dependencies.sh`:

```bash
$ docker build -t full docker-full # builds a Docker image using docker-full/
$ bash extract_dependencies.sh full # Extracts some of the filesystem in the `full` image using the list in `extracted_files` into `root/`
```

The SGX build is setup so it refers to the `root/` folder instead of using files coming from system packages.

Some dependencies are still required to be installed, these are specified in `docker-minimal`. We can use `build_in_docker.sh` to test whether the build works in a minimal dev environment:

```bash
$ docker build -t minimal docker-minimal # builds a Docker image using docker-minimal/
$ bash build_in_image.sh minimal # Runs the build inside the `minimal` image
```