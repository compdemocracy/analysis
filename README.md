
## Setup

First install Docker, however you'd like to do that for your system.

For Ubuntu-like Linux,
```
sudo apt-get install docker.io
sudo systemctl start docker
sudo systemctl enable docker
```

See https://phoenixnap.com/kb/how-to-install-docker-on-ubuntu-18-04

If you don't want to have to run Docker with sudo, see:

```
https://docs.docker.com/engine/install/linux-postinstall/
```

The rest of these instructions assume this has been run.

## Building docker image

Clone the code, cd in, then

```
make build
```

## Running docker image

Once you've built the image (see above), you can run

```
make run
```




