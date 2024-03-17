#!/bin/bash

# Script to test Jupyter notebooks

# Exit immediately if a command exits with a non-zero status.
set -e

# Find and iterate over all ipynb files in the notebooks directory and its subdirectories
find notebooks/ -name "*.ipynb" | while read -r notebook; do
    echo "Testing $notebook"
    jupyter nbconvert --to notebook --execute --output /dev/null "$notebook"
done
