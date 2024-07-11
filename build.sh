#!/bin/bash

# this script takes 1~3 mintues to complete

if [ -z $(java --version | grep 'java 17\.')]; then
    echo java 17 is not available on this device, please check
    exit 1
fi

if [ -z $(mvn --version | grep 'Maven 3\.')]; then
    echo Maven 3 is not available on this device, please check
    exit 1
fi

wget https://github.com/alibaba-edu/mpc4j/archive/refs/tags/v1.1.1.tar.gz
tar -xzf v1.1.1.tar.gz
cd mpc4j-1.1.1
mvn install

cd ..

cd atdkg
mvn clean package
cd ..

cp -t . atdkg/target/run.jar atdkg/target/computationTest.jar

echo Build completed.
echo
echo "What next?"
echo "run \`bash computationtest.sh\` to conduct computation experiments."
echo "run \`bash localtest.sh\` to conduct end-to-end experiments locally."
