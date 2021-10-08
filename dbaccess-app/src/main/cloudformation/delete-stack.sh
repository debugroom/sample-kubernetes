#!/usr/bin/env bash

stack_name="sample-kubernetes-ec2"
#stack_name="sample-kubernetes-ng"
#stack_name="sample-kubernetes-sg"
#stack_name="sample-kubernetes-vpc"

aws cloudformation delete-stack --stack-name ${stack_name}