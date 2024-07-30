#!/usr/bin/env bash

stack_name="sample-microk8s-ec2"
#stack_name="sample-microk8s-ng"
#stack_name="sample-microk8s-sg"
#stack_name="sample-microk8s-vpc"

aws cloudformation delete-stack --stack-name ${stack_name}