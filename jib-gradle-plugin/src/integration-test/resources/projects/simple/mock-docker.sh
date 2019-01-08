#!/bin/bash

# Read stdin to avoid broken pipe
cat > /dev/null

echo "Docker load called. $envvar1 $envvar2"