#!/usr/bin/env bash

stack_name="sample-kubernetes-rds"

aws cloudformation delete-stack --stack-name ${stack_name}