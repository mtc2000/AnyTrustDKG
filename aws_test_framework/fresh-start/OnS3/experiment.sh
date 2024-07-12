#!/bin/bash

set -x

ipAddress=$1
readyList=$2
#batchSize=$3
protocol=$4
experimentFolder=$5

atSize=$3

sid=$(echo "$experimentFolder" | md5sum | head -c4)

i=$(cat "$readyList" | grep -Fwn $ipAddress | cut -d':' -f1)
i=$(( i-1 ))

N=$(cat "$readyList" | wc -l)
f=$(( (N-1)/5 ))

cp "$readyList" artifacts/hosts.config

cd artifacts
mkdir -p simple-logs logs

networkInterface=$(ip -o link show | awk -F': ' '{print $2}' | grep -v '^lo')
echo traffic control overview
tc qdisc show dev $networkInterface

echo slow down traffic by 100ms
tc qdisc add dev $networkInterface root netem delay 100ms
tc qdisc show dev $networkInterface

/usr/bin/time -v \
    java \
    -Dlog4j.configuration=file:log4j.properties \
    -jar run.jar \
    $i \
    hosts.config \
    $atSize \
    $sid \
    $protocol \
    >> simple-logs/$i.stdout.log \
    2>> simple-logs/$i.stderr.log

echo remove traffic control
tc qdisc del dev $networkInterface root
tc qdisc show dev $networkInterface

sleep 1

aws configure list

stderrLog=simple-logs/$i.stderr.log
logFile=simple-logs/$i.stdout.log

if [ $(grep -ic "blockchain" "$stderrLog") -ge 1 ]; then
while true
do
    sleep 1
    aws s3 cp "$logFile" "$experimentFolder/logs/blockchain/$i.log"
    exitCode=$?
    sleep 1
    if [ "$exitCode" == "0" ]; then
        break
    fi
done
    
fi

if [ $(grep -ic "dealer" "$stderrLog") -ge 1 ]; then
while true
do
    sleep 1
    aws s3 cp "$logFile" "$experimentFolder/logs/dealer/$i.log"
    exitCode=$?
    sleep 1
    if [ "$exitCode" == "0" ]; then
        break
    fi
done
else
while true
do
    sleep 1
    aws s3 cp "$logFile" "$experimentFolder/logs/non-dealer/$i.log"
    exitCode=$?
    sleep 1
    if [ "$exitCode" == "0" ]; then
        break
    fi
done
fi

if [ $(grep -ic "plaintiff" "$stderrLog") -ge 1 ]; then
while true
do
    sleep 1
    aws s3 cp "$logFile" "$experimentFolder/logs/plaintiff/$i.log"
    exitCode=$?
    sleep 1
    if [ "$exitCode" == "0" ]; then
        break
    fi
done
else
while true
do
    sleep 1
    aws s3 cp "$logFile" "$experimentFolder/logs/non-plaintiff/$i.log"
    exitCode=$?
    sleep 1
    if [ "$exitCode" == "0" ]; then
        break
    fi
done
fi

while true
do
    sleep 1
    aws s3 cp "$logFile" "$experimentFolder/logs/stdout/$i.log"
    exitCode=$?
    sleep 1
    if [ "$exitCode" == "0" ]; then
        break
    fi
done

while true
do
    sleep 1
    aws s3 cp "$stderrLog" "$experimentFolder/logs/stderr/$i.log"
    exitCode=$?
    sleep 1
    if [ "$exitCode" == "0" ]; then
        break
    fi
done

commLog=logs/communication.log

while true
do
    sleep 1
    aws s3 cp "$commLog" "$experimentFolder/logs/comm/$i.log"
    exitCode=$?
    sleep 1
    if [ "$exitCode" == "0" ]; then
        break
    fi
done
