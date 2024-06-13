## Dockerfile for the Clojure + Jupyter Data Science Environment
## docker build -t polis-analysis:local .
## docker run -rm -p 3850:3850 -p 3860:3860 -p 3870:3870 -v $(pwd):/usr/src/app polis-analysis:local

# Start from a Clojure image
# Based on Debian 12 (bookworm)
FROM clojure:temurin-22-tools-deps

# Set environment variable for non-interactive installations
ENV DEBIAN_FRONTEND=noninteractive

# Install Python, pip, and other utilities
RUN apt-get update && \
  apt-get install -y \
  build-essential git curl wget \
  pandoc libblas-dev \
  python3 python3-pip python3-dev python3-venv libpython3-dev \
  libcairo2-dev libpango1.0-dev libjpeg-dev libgif-dev librsvg2-dev

# Create and activate a virtual environment
RUN python3 -m venv /opt/venv
ENV PATH="/opt/venv/bin:$PATH"

# Install any needed packages specified in requirements.txt
COPY requirements.txt ./
RUN pip install --no-cache-dir -r requirements.txt

# Install node for static vega png exports
RUN curl -fsSL https://deb.nodesource.com/setup_20.x | bash - && \
  apt-get install -y nodejs

# Clean up APT when done to reduce image size
RUN apt-get clean && \
  rm -rf /var/lib/apt/lists/* /tmp/* /var/tmp/*

# Verify the node/npm installation
RUN node -v && npm -v

# Install vega-cli for exporting
RUN npm i -g node-gyp canvas vega vega-lite vega-cli

# Set the working directory in the container
WORKDIR /usr/src/app

# Install Clojure dependencies
COPY deps.edn . 
RUN clojure -P

COPY src/ .

# Copy the bin directory into the container at /usr/src/app
COPY bin/ ./bin/

# Copy any top-level .py files into the container at /usr/src/app
COPY *.py .

# Copy the notebooks and data directories into the container at /usr/src/app
COPY notebooks/ ./notebooks
COPY data/ ./data

# Expose the necessary ports
# 3850: Clojure nREPL
# 3860: Oz Server
# 3870: Jupyter Notebook
EXPOSE 3850
EXPOSE 3860
EXPOSE 3870

# Start the Jupyter Notebook server and Clojure REPL
CMD ["./bin/run.sh"]
