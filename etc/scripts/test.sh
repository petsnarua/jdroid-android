#!/bin/sh

# Note the set -ev at the top. The -e flag causes the script to exit as soon as one command
# returns a non-zero exit code. The -v flag makes the shell print all lines in the script
# before executing them, which helps identify which steps failed.
set -ev

UPLOAD=$1

if [ -z "$UPLOAD" ]
then
	UPLOAD="false"
fi

# ************************
# jdroid gradle plugin
# ************************

./gradlew :jdroid-java:clean :jdroid-java:uploadArchives :jdroid-gradle-plugin:clean :jdroid-gradle-plugin:uploadArchives --configure-on-demand -PLOCAL_UPLOAD=true

# ************************
# jdroid sample server
# ************************

./gradlew :jdroid-java:clean :jdroid-java:build :jdroid-java:test :jdroid-java-http-apache:build :jdroid-java-http-apache:test :jdroid-java-http-urlconnection:build :jdroid-java-http-urlconnection:test :jdroid-javaweb:clean :jdroid-javaweb:build :jdroid-javaweb:test :jdroid-sample-server:build --configure-on-demand

# ************************
# jdroid sample android
# ************************

#./gradlew :jdroid-android:clean :jdroid-android:build :jdroid-android:testDebug :jdroid-android-facebook:clean :jdroid-android-facebook:build :jdroid-android-facebook:testDebug :jdroid-android-google-maps:clean :jdroid-android-google-maps:build :jdroid-android-google-maps:testDebug :jdroid-android-google-gcm:clean :jdroid-android-google-gcm:build :jdroid-android-google-gcm:testDebug :jdroid-sample-android:check :jdroid-sample-android:assembleUat :jdroid-sample-android:countMethodsSummary

# ************************
# Upload Snapshot to Maven repository
# ************************

if [ "$UPLOAD" = "true" ]
then
	./gradlew :jdroid-gradle-plugin:uploadArchives :jdroid-java:uploadArchives :jdroid-java-http-apache:uploadArchives :jdroid-java-http-urlconnection:uploadArchives :jdroid-javaweb:uploadArchives --configure-on-demand
	#./gradlew :jdroid-android:uploadArchives :jdroid-android-facebook:uploadArchives :jdroid-android-google-maps:uploadArchives :jdroid-android-google-gcm:uploadArchives
fi
