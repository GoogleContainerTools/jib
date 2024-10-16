#!/bin/bash

if [[ "$1" == "info" ]]; then
  # Output the JSON string
  echo "{\"OSType\":\"linux\",\"Architecture\":\"x86_64\"}"
  exit 0
fi

# Read stdin to avoid broken pipe
cat > /dev/null

echo "Docker load called. $envvar1 $envvar2"