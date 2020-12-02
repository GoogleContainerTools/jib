#!/bin/sh

export JIB_OPTS=-agentlib:native-image-agent=config-output-dir=$HOME/workspace4E/jib/jib-cli/graalvm11

cd $HOME/A
jib --target=docker://foo --stacktrace --verbosity=debug jar C.jar
