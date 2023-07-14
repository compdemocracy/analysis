FROM clojure:openjdk-11-tools-deps-bullseye

# Updating Ubuntu packages & misc installs
# ========================

RUN apt-get -qq update &&\
    apt-get -qq -y install curl wget bzip2

# Pandoc for fun and profit
RUN apt-get -y install pandoc

# This should make tech.ml stuff fast for things like pca/svd
RUN apt-get install -y libblas-dev


# Installing node for static vega png exports
# ===============================================

ENV NODE_VERSION=14.5.0
RUN apt-get install -y curl
RUN curl -o- https://raw.githubusercontent.com/creationix/nvm/v0.34.0/install.sh | bash
ENV NVM_DIR=/root/.nvm
RUN . "$NVM_DIR/nvm.sh" && nvm install ${NODE_VERSION}
RUN . "$NVM_DIR/nvm.sh" && nvm use v${NODE_VERSION}
RUN . "$NVM_DIR/nvm.sh" && nvm alias default v${NODE_VERSION}
ENV PATH="/root/.nvm/versions/node/v${NODE_VERSION}/bin/:${PATH}"
RUN node --version
RUN npm --version

# Install vega-cli for exporting
RUN apt-get install -y build-essential libcairo2-dev libpango1.0-dev libjpeg-dev libgif-dev librsvg2-dev g++
#RUN apt-get install -y zlibc zlib1g-dev zlib1g

RUN npm install -g node-gyp
RUN npm install -g --unsafe-perm canvas
RUN npm install -g --unsafe-perm vega vega-lite vega-cli



# Setting up python environment
# =============================

RUN curl -sSL https://repo.continuum.io/miniconda/Miniconda3-latest-Linux-x86_64.sh -o /tmp/miniconda.sh &&\
  bash /tmp/miniconda.sh -bfp /usr/local &&\
  rm -rf /tmp/miniconda.sh &&\
  conda install -y python=3 &&\
  conda update conda

RUN apt-get -qq -y autoremove &&\
  apt-get autoclean &&\
  rm -rf /var/lib/apt/lists/* /var/log/dpkg.log &&\
  conda clean --all --yes

ENV PATH /opt/conda/bin:$PATH

RUN conda create -n pyclj python=3.8
RUN conda install -n pyclj scikit-learn
RUN conda install -n pyclj numpy
RUN conda run -n pyclj python3 -mpip install numba==0.57.0
RUN conda install -n pyclj -c conda-forge umap-learn
RUN conda install -n pyclj -c anaconda importlib-metadata

RUN conda install -n pyclj seaborn
RUN conda install -n pyclj matplotlib
RUN conda install -n pyclj altair
RUN conda install -n pyclj jupyter

## To install pip packages into the pyclj environment do
#RUN conda run -n pyclj python3 -mpip install trimap

SHELL ["conda", "run", "-n", "pyclj", "/bin/bash", "-c"]
ENV LD_LIBRARY_PATH "/usr/local/envs/pyclj:/usr/local/lib:$LD_LIBRARY_PATH"
# Would like to be able to do something like this but doesn't actually work:
# ENV LD_LIBRARY_PATH=$LD_LIBRARY_PATH:"$(python3-config --prefix)/lib"


# Finalizing setup
# ========================

# Sketch for improved permissions
#ARG UID=1000
#ARG GID=133
#ARG USERNAME=analyst

#RUN groupadd -g $GID $USERNAME
#RUN useradd -u $UID -g $GID $USERNAME
#RUN mkdir /home/$USERNAME && chown $USERNAME:$USERNAME /home/$USERNAME
#USER $USERNAME
# Means using a different directory though

# Everything after this will get rerun even if the commands haven't changed, since data could change
WORKDIR /app

# Make sure deps are pre-installed
COPY deps.edn .
RUN clojure -P

COPY src/ src/

# Build a uberjar target for clojupyter kernel
#RUN clojure -Spom
#RUN clojure -A:depstar -m hf.depstar.uberjar clojupyter-standalone.jar -C -m polis.main
#RUN clojure -m clojupyter.cmdline install --ident polis-clojupyter-kernel --jarfile clojupyter-standalone.jar

EXPOSE 3850
EXPOSE 3860
EXPOSE 3870
EXPOSE 3880


COPY . .

# Systems go
#CMD ["conda", "run", "-n", "pyclj", "./bin/run.sh"]
CMD ["conda", "run", "-n", "pyclj", "clojure", "-M:cider-nrepl"]

