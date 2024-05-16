#!/usr/bin/bash

TEMP_FILE=$(mktemp)
BACKUP_FILE=$(mktemp)

sed 's/tech.ml/tech.v3/g' $1 \
  | sed 's/tech.v2/tech.v3/g' \
  | sed 's/libpython-clj\./libpython-clj2\./g' \
  | sed 's/py\/as-numpy/py\/as-python/g' \
  | sed 's/py\/->numpy/py\/->python/g' \
  > $TEMP_FILE
  #| sed 's/py\/call-kw/py\/call-attr-kw/g' \


if grep "ds\/sort-by" $TEMP_FILE -n -C 3; then
  echo "  WARNING => switch from (sort-by key-fn ds) -> (sort-by ds key-fn)"
fi

if grep "ds\/column-map" $TEMP_FILE -n -C 3; then
  echo "  WARNING => switch from (column-map ds out-col f col1 col2 col3 ...) -> (column-map ds out-col f [col1 col2 col3 ...]),"
fi

if grep "ds\/filter" $TEMP_FILE -n -C 3; then
  echo "  WARNING => switch from (filter f ds) -> (filter ds f)"
fi

if grep "py\/set-item\!" $TEMP_FILE -n -C 3; then
  echo "  WARNING => check to see if you need to switch to py/set-attr!"
fi


echo "\n"
echo "Backing up to backup file:" $BACKUP_FILE
cp $1 $BACKUP_FILE

# Copy back
echo "Copying contents from: $TEMP_FILE to $1"
cp $TEMP_FILE $1

