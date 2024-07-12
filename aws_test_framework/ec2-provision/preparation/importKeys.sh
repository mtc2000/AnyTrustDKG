#!/bin/bash

# This script will import a public key (./ReplaceThisByYourOwnKey.pub) to all regions in `regions.txt` with the key name `ReplaceThisByYourOwnKey`

# To convert a `.pem`-format private key to a `.pub`-format public key, try the following:
# ssh-keygen -y -f private_key_name.pem > public_key_name.pub 

# Check if the regions file exists
if [ ! -f "regions.txt" ]; then
    echo "Error: regions.txt file not found."
    exit 1
fi

# Read regions from the file into an array
target_regions=($(cat regions.txt))

# Loop through target regions and copy AMI if source and target regions are different
for target_region in "${target_regions[@]}";
do
    aws ec2 import-key-pair \
    --no-dry-run \
    --region "$target_region" \
    --key-name ReplaceThisByYourOwnKey \
    --public-key-material fileb://./ReplaceThisByYourOwnKey.pub \
    --output text
done
