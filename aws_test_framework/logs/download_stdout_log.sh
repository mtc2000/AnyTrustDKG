#!/bin/bash

name=$(echo $1 | xargs -I{} basename {} | sed 's/\/$//')
read -p "$name continue?"

aws s3 cp s3://replace-this-by-your-bucket-name/replace-this-by-any-name/ stdout-logs --exclude "**" --include "**/logs/stdout/**" --recursive --dryrun | tee download-comm.log | wc -l

read -p "enter to conitnue"

aws s3 cp s3://replace-this-by-your-bucket-name/replace-this-by-any-name/ stdout-logs --exclude "**"  --include "**/logs/stdout/**" --recursive --only-show-errors
