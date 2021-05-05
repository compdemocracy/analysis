
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
You'll also want `docker-compose` installed to simplify the process of buiding and running the docker containers.
There _is_ also a `Makefile` with `make build`, `make run` and `make kill` commands which can be used, but these are somewhat more cantankerous to work with, and we recommend using `docker-compose` instead.


## Usage

### Building with `docker-compose`

To get started, you'll need to build and run the docker images, which you can do with `docker-compose`.

In general, you'll want to run the following:

```
docker-compose -f docker-compose.yml -f docker-compose.dev.yml up
```

The `*.dev.yml` file mounts your local checkout so that data, notebooks, and build targets can be mirrored
to your local machine, which is generally what you want for actually hacking on the notebooks and getting data
in and out of the docker environment.

However, if you are running on a remote machine, or want to pre-build an image for distribution on (e.g.) docker-hub, this may not be desirable.
If this sounds like you, you can simply run `docker-compose up`.

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
Most editors with good support for Clojure will be able to connect to this port automatically using the `.nrepl-port` file (as long as you ran with the `dev.yml` config).
If not, you should be able to manually establish a connection by specifying hostname and port.
This will let you evaluate and interact with code directly from your editor.
If you'd rather, you can install `leiningen` and start a repl with `lein repl :connect localhost:3850`, and interact with Clojure through a traditional REPL process.

Once you have this running you can look at `dev/user.clj`, which has a commented out line for running `(oz/build! ...)`.
If you execute this, it will build the Clojure/[Oz](https://github.com/metasoarous/oz) notebooks in `notebooks/oz`, and compile html output to `notebooks/build`.
This build process features live code reloading, so that as you save changes to the notebook file, a live view of the results can be accessed at http://localhost:3860.

**NOTE:** Due to a compilation bug with `libpython-clj`, you may have to evaluate the `polis.math` namespace before you can run the `user` namespace.


### Local Data & Notebooks

There are a couple of example datasets in `data`, and some example notebooks in `notebooks`.
If you have data or analysis you'd like to keep out of git, the `local` directory is in the `.gitignore`.

In particular, Oz/Clojure are set up to build from `local/notebooks` to `local/build`, and you can put data in `local/data`.


### _Re_building with `docker-compose`

You generally shouldn't need to rebuild if you're using the `*.dev.yml` config (since local changes to data
and notebooks are mirrored into the container).
However, there are a few cases in which you might need to rebuild:
* the python dependencies have updated and you'd like access to those
* you're using the Clojure API from Jupyter, and need access to updated Clojure code or dependencies (using Clojure from Clojupyter requires AOT JVM compilation)
* the Dockerfile has changed in some other (nontrivial) way



</br>

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


