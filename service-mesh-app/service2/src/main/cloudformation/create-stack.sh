#!/usr/bin/env bash


stack_name="sample-kubernetes-rds"
template_path="rds-cfn.yml"
#aws cloudformation create-stack --stack-name ${stack_name} --template-body file://${template_path} --capabilities CAPABILITY_IAM
# It is better cloudformation deploy option because command can execute even if stack existing(no need to delete existing stack).

if [ "$parameters" == "" ]; then
    aws cloudformation deploy --stack-name ${stack_name} --template-file ${template_path} --capabilities CAPABILITY_IAM
else
    aws cloudformation deploy --stack-name ${stack_name} --template-file ${template_path} --parameter-overrides ${parameters} --capabilities CAPABILITY_NAMED_IAM
fi