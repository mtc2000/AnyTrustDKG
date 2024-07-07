#!/bin/bash

yum install -y iproute-tc
yum install -y `yum search java | grep java-17 | head -n 1 | cut -d' ' -f1`
