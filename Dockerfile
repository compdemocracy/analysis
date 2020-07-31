FROM clojure:openjdk-11-tools-deps

EXPOSE 3850
EXPOSE 3860
EXPOSE 3870
EXPOSE 3880


RUN echo "deb http://fr.archive.ubuntu.com/ubuntu bionic main" >> /etc/apt/sources.list &&\
    apt-key adv --keyserver keyserver.ubuntu.com --recv-keys 3B4FE6ACC0B21F32 &&\
    apt-get update &&\
    apt-get install libpython3.6-dev python3-pip -y --allow-unauthenticated

# This should make tech.ml stuff fast for things like pca/svd
RUN apt-get install -y libblas-dev

# Preinstall these packages, so later requirements.txt install will be faster
RUN pip3 install seaborn &&\
    pip3 install matplotlib &&\
    pip3 install sklearn &&\
    pip3 install numpy &&\
    pip3 install umap-learn &&\
    pip3 install trimap &&\
    pip3 install altair &&\
    pip3 install jupyter

RUN clojure -Sdeps '{:deps {org.clojure/clojure {:mvn/version "1.10.0"} \
        techascent/tech.ml.dataset {:mvn/version "3.01" \
                                    :exclusions [org.slf4j/slf4j-api]} \
        semantic-csv {:mvn/version "0.2.1-alpha1"} \
        net.mikera/core.matrix {:mvn/version "0.62.0"} \
        clj-python/libpython-clj {:mvn/version "1.45"} \
        org.clojure/tools.deps.alpha {:mvn/version "0.6.496" \
                                      :exclusions [org.slf4j/slf4j-nop]} \
        cider/cider-nrepl {:mvn/version "0.21.1"} \
        metasoarous/oz {:mvn/version "1.6.0-alpha14"} \
        clojupyter {:mvn/version "0.3.2"}}}' \
   -e "(clojure.core/println :deps-downloaded)"


# Installing node
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
RUN apt-get install -y zlibc zlib1g-dev zlib1g

RUN npm install -g node-gyp
RUN npm install -g --unsafe-perm canvas
RUN npm install -g --unsafe-perm vega vega-lite vega-cli

# Pandoc for fun and profit
RUN apt-get -y install pandoc

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
COPY . .

# Make sure deps are pre-installed
RUN pip3 install -r requirements.txt
RUN clojure -e "(clojure.core/println :deps-downloaded)"

# Build a uberjar target for clojupyter kernel
RUN clojure -Spom
RUN clojure -A:depstar -m hf.depstar.uberjar clojupyter-standalone.jar -C -m polis.main
RUN clojure -m clojupyter.cmdline install --ident polis-clojupyter-kernel --jarfile clojupyter-standalone.jar


# Systems go
CMD bin/run.sh


