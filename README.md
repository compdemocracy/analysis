
# Polis Analysis

This (WIP) repository contains code for analyzing Polis conversation data.

Specifically, it contains:

* An API of Clojure functions for processing data (see `src/polis/math.clj`)
  * [libpython-clj](https://github.com/clj-python/libpython-clj) bindings for running python tools like UMAP, Trimap & Leiden
* A set of Jupyter notebooks, with both Clojure & Python kernels, in `notebooks/jupyter`
* A set of Oz notebooks in `notebooks/oz`
* A `Dockerfile` which should assist in making all of the above run reproducibly


## Prereqs

If you'd like to use Docker to run this code, you'll obviously want to have Docker installed on your system, preferably set up so that you don't need to run with `sudo`.
See the end of this document for instructions on this.

You may also want to have `make` installed to simplify the process of running somewhat.
If you don't have this installed yet, please try installing with your favorite package manager.


## Usage

### Building docker image

Before you can run anything from Docker, you have to build the Docker image.
First, clone the code, cd in, then run

```
make build
```

### Running docker image

Once you've built the image (see above), you can run with

```
make run
```

### Building and running

Of course, when you build, you'll typically want to run immediately thereafter, so you can also do

```
make build && make run
```

### Using `docker-compose`

You can also use `docker-compose` instead of make:

Generally, you'll want to run

```
docker-compose -f docker-compose.yml -f docker-compose.dev.yml up
```

Add the `dev.yml` file mounts your local checkout so that data, notebooks, and build targets can be mirrored
to your local machine.
This may not be desireable if you are running on a remote machine, or want to pre-build an image for
distribution on (e.g.) docker-hub.
If this sounds like you, you can simply run `docker-compose up`

To rebuilt, add the `--build` flag.

### Connecting

Once the docker container is running, you should see some lines printed like

```
    To access the notebook, open this file in a browser:
        file:///root/.local/share/jupyter/runtime/nbserver-7-open.html
    Or copy and paste one of these URLs:
        http://b1640360dd7c:3870/?token=e9e17fcbba7adc211472eacce9319af016ad5b5a1cd6643a
     or http://127.0.0.1:3870/?token=e9e17fcbba7adc211472eacce9319af016ad5b5a1cd6643a
nREPL server started on port 3850 on host 0.0.0.0 - nrepl://0.0.0.0:3850
```

#### Jupyter

To use the Jupyter notebooks, copy the `127.0.0.1` url and paste into your browser.
You should now be able to create notebooks with either the Python or Clojupyter kernel, depending on what language you prefer to work with.


#### Oz/Clojure

If you're using Clojure, you can connect to the REPL using the port printed above.
Most editors with good support for Clojure will be able to connect to this port automatically using the `.nrepl-port` file.
If not, you should be able to manually establish a connection by specifying hostname and port.
This will let you evaluate and interact with code directly from your editor.
If you'd rather, you can install `leiningen` and start a repl with `lein repl :connect localhost:3850`, and interact with Clojure through a traditional REPL process.

Once you have this running you can look at `dev/user.clj`, which has a commented out line for running `(oz/build! ...)`.
If you execute this, it will build the Clojure/[Oz](https://github.com/metasoarous/oz) notebooks in `notebooks/oz`, and compile html output to `notebooks/build`.
This build process features live code reloading, so that as you save changes to the notebook file, a live view of the results can be accessed at http://localhost:3860.

**NOTE:** Due to a compilation bug with `libpython-clj`, you may have to evaluate the `polis.math` namespace before you can run the `user` namespace.


### Stopping

Docker containers can be a bit of a pain to stop.
When you're ready to stop the process, open up another terminal shell and run

```
make kill
```


### Local Data & Notebooks

There are a couple of example datasets in `data`, and some example notebooks in `notebooks`.
If you have data or analysis you'd like to keep out of git, the `local` directory is in the `.gitignore`.

In particular, Oz/Clojure are set up to build from `local/notebooks` to `local/build`, and you can put data in `local/data`.


## Installing Docker

For Ubuntu-like Linux,

```
sudo apt-get install docker.io
sudo systemctl start docker
sudo systemctl enable docker
```

A more complete guide can be found here: https://phoenixnap.com/kb/how-to-install-docker-on-ubuntu-18-04

If you don't want to have to run Docker with sudo, see: https://docs.docker.com/engine/install/linux-postinstall

The rest of these instructions assume that this has been performed, and that you don't need sudo access to
run.


