#!/bin/bash

# this script archive the content of `OnS3` and upload it to S3.

if [ -z "$1" ]; then
    echo 'usage: archive.sh <version>'
    echo '<version> is missing'
    exit 1
fi

cd OnS3
tar czf ../binary.$1.tar.gz *
cd ..

read -p 'upload? (y/n): ' upload
if [ "$upload" == "y" ]; then
    aws s3 cp binary.$1.tar.gz s3://replace-this-by-your-bucket-name
fi