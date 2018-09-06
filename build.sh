#!/bin/sh
# Build Jib

quickMode=false
mavenOptions=""
gradleOptions=""

usage()
{
  eval 1>&2
  echo "Simple builder for Jib for jib-core, jib-plugins-common, jib-maven-plugin, and jib-gradle-plugin"
  echo "use: $0 [-qe] [clean | core | plugins | maven | gradle | all | it]"
  echo "  'all' is the same as 'core plugins gradle maven'"
  echo "  -q  quick mode: skip tests, formatting"
  echo "  -e  show error information (mvn: -e, gradle: --stacktrace --info)"
  exit 1
}

# doBuild: Run a command in a directory
# $1 = directory
# $2... = build command
doBuild() {
  (directory="$1"; shift; echo ">>> (cd $directory; $*)"; cd "$directory" && eval '"$@"')
}

while getopts qe c; do
  case $c in
  q)  quickMode=true;;
  e)  mavenOptions="$mavenOptions -e"
      gradleOptions="$gradleOptions --stacktrace --info"
      ;;
  \?) usage;;
  esac
done
shift `expr $OPTIND - 1`

if [ $# -eq 0 ]; then
  set -- core plugins gradle maven
fi

set -e  # exit on error
for target in "$@"; do
  case "$target" in
    clean)
      doBuild jib-core ./gradlew $gradleOptions clean
      doBuild jib-plugins-common ./gradlew $gradleOptions clean
      doBuild jib-gradle-plugin ./gradlew $gradleOptions clean
      doBuild jib-maven-plugin ./mvnw $mavenOptions clean
      ;;

    core)
      if [ "$quickMode" = false ]; then
        doBuild jib-core           ./gradlew $gradleOptions googleJavaFormat build
      else
        doBuild jib-core  ./gradlew $gradleOptions build \
            --exclude-task test --exclude-task check
      fi
      ;;

    plugins)
      if [ "$quickMode" = false ]; then
        doBuild jib-plugins-common ./gradlew $gradleOptions googleJavaFormat build
      else
        doBuild jib-plugins-common ./gradlew $gradleOptions build \
            --exclude-task test --exclude-task check
      fi
      ;;

    maven)
      if [ "$quickMode" = false ]; then
        doBuild jib-maven-plugin   ./mvnw $mavenOptions fmt:format install -U
      else
        # jib-maven-plugin pulls in jib-core directly
        doBuild jib-maven-plugin   ./mvnw $mavenOptions -Dcheckstyle.skip -Dfmt.skip -DskipTests install -U
      fi
      ;;

    gradle)
      if [ "$quickMode" = false ]; then
        doBuild jib-gradle-plugin  ./gradlew $gradleOptions googleJavaFormat build install
      else
        # jib-gradle-plugin pulls in jib-core directly
        doBuild jib-gradle-plugin  ./gradlew $gradleOptions build install \
            --exclude-task test --exclude-task check
      fi
      ;;

    it)
      doBuild jib-core  ./gradlew $gradleOptions integrationTest
      doBuild jib-maven-plugin   ./mvnw $mavenOptions -Pintegration-tests verify -U
      doBuild jib-gradle-plugin  ./gradlew $gradleOptions integrationTest
      ;;
  esac
done
