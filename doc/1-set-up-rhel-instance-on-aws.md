
### MicroK8s

#### Set up RHEL9 AWS EC2 instance

<!--
**NOTE:** 本手順はEC2インスタンスタイプがベアメタルのときのみ有効な手段であることに注意。通常の仮想インスタンスだと、Nested Virtualizationをサポートしないため、仮想化サービスの起動時にCPU VirtuallizationがFAILとなり、CRCをセットアップできない。KVM仮想マシンの起動をQEMUで起動する方法を検証する。See : [仮想化の有効化](https://docs.redhat.com/ja/documentation/red_hat_enterprise_linux/9/html/configuring_and_managing_virtualization/assembly_enabling-virtualization-in-rhel-9_configuring-and-managing-virtualization#proc_enabling-virtualization-in-rhel-9_assembly_enabling-virtualization-in-rhel-9)
-->  

----

CloudFormation実行権限が付与されたAWS認証キーが設定された環境で以下のコマンドを実行する。
それぞれ以下の順序でテンプレートを更新する。

1. VPC
1. Security Group
1. NAT Gateway
1. EC2

テンプレートを実行するシェルスクリプト。スタックを適宜コメントアウトし、順次実行。

```bash
#!/usr/bin/env bash


stack_name="sample-microk8s-ec2"
#stack_name="sample-microk8s-ng"
#stack_name="sample-microk8s-sg"
#stack_name="sample-microk8s-vpc"
template_path="ec2-cfn.yml"
#template_path="ng-cfn.yml"
#template_path="sg-cfn.yml"
#template_path="vpc-cfn.yml"
parameters="EnvType=Dev"
#aws cloudformation create-stack --stack-name ${stack_name} --template-body file://${template_path} --capabilities CAPABILITY_IAM
# It is better cloudformation deploy option because command can execute even if stack existing(no need to delete existing stack).

if [ "$parameters" == "" ]; then
    aws cloudformation deploy --stack-name ${stack_name} --template-file ${template_path} --capabilities CAPABILITY_IAM
else
    aws cloudformation deploy --stack-name ${stack_name} --template-file ${template_path} --parameter-overrides ${parameters} --capabilities CAPABILITY_NAMED_IAM
fi
```

- vpc

```yaml
AWSTemplateFormatVersion: '2010-09-09'

Description: Sample MicroK8s template with YAML

Parameters:
  VPCName:
    Description: Target VPC Stack Name
    Type: String
    MinLength: 1
    MaxLength: 255
    AllowedPattern: ^[a-zA-Z][-a-zA-Z0-9]*$
    Default: sample-microk8s-vpc
  VPCCiderBlock:
    Description: CiderBlock paramater for VPC
    Type: String
    MinLength: 9
    MaxLength: 18
    AllowedPattern: (\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})/(\d{1,2})
    Default: 172.7.0.0/16
  PublicSubnet1CiderBlock:
    Description: CiderBlock paramater for VPC
    Type: String
    MinLength: 9
    MaxLength: 18
    AllowedPattern: (\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})/(\d{1,2})
    Default: 172.7.1.0/24
  PublicSubnet2CiderBlock:
    Description: CiderBlock paramater for VPC
    Type: String
    MinLength: 9
    MaxLength: 18
    AllowedPattern: (\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})/(\d{1,2})
    Default: 172.7.2.0/24
  PrivateSubnet1CiderBlock:
    Description: CiderBlock paramater for VPC
    Type: String
    MinLength: 9
    MaxLength: 18
    AllowedPattern: (\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})/(\d{1,2})
    Default: 172.7.3.0/24
  PrivateSubnet2CiderBlock:
    Description: CiderBlock paramater for VPC
    Type: String
    MinLength: 9
    MaxLength: 18
    AllowedPattern: (\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})/(\d{1,2})
    Default: 172.7.4.0/24

Resources:
  VPC:
    Type: AWS::EC2::VPC
    Properties:
      CidrBlock: !Sub ${VPCCiderBlock}
      InstanceTenancy: default
      EnableDnsSupport: true
      EnableDnsHostnames: true
      Tags:
        - Key: Name
          Value: !Sub ${VPCName}

  PublicSubnet1:
    Type: AWS::EC2::Subnet
    Properties:
      CidrBlock: !Sub ${PublicSubnet1CiderBlock}
      VpcId: !Ref VPC
      AvailabilityZone: !Select [ 0, !GetAZs '' ]
      Tags:
        - Key: Name
          Value: !Sub ${VPCName}-PublicSubnet1

  PublicSubnet2:
    Type: AWS::EC2::Subnet
    Properties:
      CidrBlock: !Sub ${PublicSubnet2CiderBlock}
      VpcId: !Ref VPC
      AvailabilityZone: !Select [ 1, !GetAZs '' ]
      Tags:
        - Key: Name
          Value: !Sub ${VPCName}-PublicSubnet2

  PrivateSubnet1:
    Type: AWS::EC2::Subnet
    Properties:
      CidrBlock: !Sub ${PrivateSubnet1CiderBlock}
      VpcId: !Ref VPC
      AvailabilityZone: !Select [ 0, !GetAZs '' ]
      Tags:
        - Key: Name
          Value: !Sub ${VPCName}-PrivateSubnet1

  PrivateSubnet2:
    Type: AWS::EC2::Subnet
    Properties:
      CidrBlock: !Sub ${PrivateSubnet2CiderBlock}
      VpcId: !Ref VPC
      AvailabilityZone: !Select [ 1, !GetAZs '' ]
      Tags:
        - Key: Name
          Value: !Sub ${VPCName}-PrivateSubnet2

  IGW:
    Type: AWS::EC2::InternetGateway
    Properties:
      Tags:
        - Key: Name
          Value: !Sub ${VPCName}-IGW

  IGWAttach:
    Type: AWS::EC2::VPCGatewayAttachment
    Properties:
      InternetGatewayId: !Ref IGW
      VpcId: !Ref VPC

  CustomRouteTable:
    Type: AWS::EC2::RouteTable
    Properties:
      VpcId: !Ref VPC
      Tags:
        - Key: Name
          Value: !Sub ${VPCName}-PublicRoute

  CustomRoute:
    Type: AWS::EC2::Route
    Properties:
      RouteTableId: !Ref CustomRouteTable
      DestinationCidrBlock: 0.0.0.0/0
      GatewayId: !Ref IGW

  PublicSubnet1Association:
    Type: AWS::EC2::SubnetRouteTableAssociation
    Properties:
      SubnetId: !Ref PublicSubnet1
      RouteTableId: !Ref CustomRouteTable

  PublicSubnet2Association:
    Type: AWS::EC2::SubnetRouteTableAssociation
    Properties:
      SubnetId: !Ref PublicSubnet2
      RouteTableId: !Ref CustomRouteTable

Outputs:
  VPC:
    Description: VPC ID
    Value: !Ref VPC
    Export:
      Name: !Sub ${VPCName}-VPCID

  PublicSubnet1:
    Description: PublicSubnet1
    Value: !Ref PublicSubnet1
    Export:
      Name: !Sub ${VPCName}-PublicSubnet1

  PublicSubnet1Arn:
    Description: PublicSubnet1Arn
    Value: !Sub
      - arn:aws:ec2:${AWS::Region}:${AWS::AccountId}:subnet/${PublicSubnet1}
      - PublicSubnet1: !Ref PublicSubnet1
    Export:
      Name: !Sub ${VPCName}-PublicSubnet1Arn

  PublicSubnet2:
    Description: PublicSubnet2
    Value: !Ref PublicSubnet2
    Export:
      Name: !Sub ${VPCName}-PublicSubnet2

  PublicSubnet2Arn:
    Description: PublicSubnet2Arn
    Value: !Sub
      - arn:aws:ec2:${AWS::Region}:${AWS::AccountId}:subnet/${PublicSubnet2}
      - PublicSubnet2: !Ref PublicSubnet2
    Export:
      Name: !Sub ${VPCName}-PublicSubnet2Arn

  PrivateSubnet1:
    Description: PrivateSubnet1
    Value: !Ref PrivateSubnet1
    Export:
      Name: !Sub ${VPCName}-PrivateSubnet1

  PrivateSubnet1Arn:
    Description: PrivateSubnet1Arn
    Value: !Sub
      - arn:aws:ec2:${AWS::Region}:${AWS::AccountId}:subnet/${PrivateSubnet1}
      - PrivateSubnet1: !Ref PrivateSubnet1
    Export:
      Name: !Sub ${VPCName}-PrivateSubnet1Arn

  PrivateSubnet2:
    Description: PrivateSubnet2
    Value: !Ref PrivateSubnet2
    Export:
      Name: !Sub ${VPCName}-PrivateSubnet2

  PrivateSubnet2Arn:
    Description: PrivateSubnet2Arn
    Value: !Sub
      - arn:aws:ec2:${AWS::Region}:${AWS::AccountId}:subnet/${PrivateSubnet2}
      - PrivateSubnet2: !Ref PrivateSubnet2
    Export:
      Name: !Sub ${VPCName}-PrivateSubnet2Arn

  EnvironmentRegion:
    Description: Dev Environment Region
    Value: !Sub ${AWS::Region}
    Export:
      Name: !Sub ${VPCName}-EnvironmentRegion

```

- Security Group

```yaml
AWSTemplateFormatVersion: '2010-09-09'

Description: CloudFormation template with YAML - SecurityGroup depends on vpc-cfn.yml.

Parameters:
  VPCName:
    Description: Target VPC Stack Name
    Type: String
    MinLength: 1
    MaxLength: 255
    AllowedPattern: ^[a-zA-Z][-a-zA-Z0-9]*$
    Default: sample-microk8s-vpc
  VPCCiderBlock:
    Description: CiderBlock paramater for VPC
    Type: String
    MinLength: 9
    MaxLength: 18
    AllowedPattern: (\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})/(\d{1,2})
    Default: 172.7.0.0/16

Resources:
  SecurityGroupEC2:
    Type: AWS::EC2::SecurityGroup
    Properties:
      GroupName: SecurityGroupEC2
      GroupDescription: SSHAccess
      VpcId:
        Fn::ImportValue: !Sub ${VPCName}-VPCID
      Tags:
        - Key : Name
          Value: !Sub ${VPCName}-SecurityGroupEC2

Outputs:
  SecurityGroupEC2:
    Description: Security Group for EC2
    Value: !Ref SecurityGroupEC2
    Export:
      Name: !Sub ${VPCName}-SecurityGroupEC2

```

- NAT Gateway

```yaml
AWSTemplateFormatVersion: '2010-09-09'

Description: CloudFormation template with YAML - NatGateway Depends on vpc-cfn.yml

Parameters:
  VPCName:
    Description: Target VPC Stack Name
    Type: String
    MinLength: 1
    MaxLength: 255
    AllowedPattern: ^[a-zA-Z][-a-zA-Z0-9]*$
    Default: sample-microk8s-vpc

Resources:
  NatGWEIP:
    Type: AWS::EC2::EIP
    Properties:
      Domain:
        Fn::ImportValue: !Sub ${VPCName}-VPCID

  NatGW:
    Type: AWS::EC2::NatGateway
    Properties:
      AllocationId: !GetAtt NatGWEIP.AllocationId
      SubnetId:
        Fn::ImportValue: !Sub ${VPCName}-PublicSubnet1
      Tags:
        - Key: Name
          Value: !Sub ${VPCName}-NatGW

  MainRouteTable:
    Type: AWS::EC2::RouteTable
    Properties:
      VpcId:
        Fn::ImportValue: !Sub ${VPCName}-VPCID
      Tags:
        - Key: Name
          Value: !Sub ${VPCName}-PrivateRoute

  MainRoute:
    Type: AWS::EC2::Route
    Properties:
      RouteTableId: !Ref MainRouteTable
      DestinationCidrBlock: 0.0.0.0/0
      NatGatewayId: !Ref NatGW

  PrivateSubnet1Association:
    Type: AWS::EC2::SubnetRouteTableAssociation
    Properties:
      SubnetId:
        Fn::ImportValue: !Sub ${VPCName}-PrivateSubnet1
      RouteTableId: !Ref MainRouteTable

  PrivateSubnet2Association:
    Type: AWS::EC2::SubnetRouteTableAssociation
    Properties:
      SubnetId:
        Fn::ImportValue: !Sub ${VPCName}-PrivateSubnet2
      RouteTableId: !Ref MainRouteTable

Outputs:
  MainRouteTable:
    Description: Backend Route Table
    Value: !Ref MainRouteTable
    Export:
      Name: !Sub ${VPCName}-Backend-RouteTable

```

- RHEL on EC2

```yaml
AWSTemplateFormatVersion: '2010-09-09'

Description: CloudFormation template with YAML - This EC2 instance Depends on vpc-cfn.yml, sg-cfn.yml, ng-cfn.yml

Parameters:
  VPCName:
    Description: Target VPC Stack Name
    Type: String
    MinLength: 1
    MaxLength: 255
    AllowedPattern: ^[a-zA-Z][-a-zA-Z0-9]*$
    Default: sample-microk8s-vpc
  EC2AMI:
    Description: AMI ID
    #    Type: AWS::SSM::Parameter::Value<AWS::EC2::Image::Id>
    #    Default: /aws/service/ecs/optimized-ami/amazon-linux-2/recommended/image_id
    #     Default: ami-0fe22bffdec36361c
    Type: String
    #   Red Hat Enterprise Linux 9(HVM), SSD Volume Type
    #   See https://docs.redhat.com/ja/documentation/red_hat_openshift_local/2.2/html-single/getting_started_guide/index#minimum-system-requirements-hardware_gsg:
    Default: ami-04d3ba818c434b384
  EnvType:
    Description: Which environments to deploy your service.
    Type: String
    AllowedValues:
      - Local
      - Dev
      - Staging
      - Production
    Default: Dev

Mappings:
  EC2DefinitionMap:
    Production:
      "InstanceType" : "r6a.xlarge"
      "DesiredCapacity" : 1
      "EC2InstanceMaxSize" : 1
      "KeyPairName" : "test"
    Staging:
      "InstanceType": "r6a.xlarge"
      "DesiredCapacity": 1
      "EC2InstanceMaxSize" : 1
      "KeyPairName": "test"
    Dev:
      "InstanceType": "r6a.xlarge"
      "DesiredCapacity": 1
      "EC2InstanceMaxSize" : 1
      "KeyPairName": "test"
    # See https://docs.redhat.com/ja/documentation/red_hat_openshift_local/2.2/html-single/getting_started_guide/index#minimum-system-requirements-hardware_gsg:
    Local:
      "InstanceType": "r6a.xlarge"
      "DesiredCapacity": 1
      "EC2InstanceMaxSize" : 1
      "KeyPairName": "test"

Resources:
  EC2Role:
    Type: AWS::IAM::Role
    Properties:
      Path: /
      AssumeRolePolicyDocument:
        Statement:
          - Action: sts:AssumeRole
            Effect: Allow
            Principal:
              Service: ec2.amazonaws.com
      ManagedPolicyArns:
        - arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore

  IamInstanceProfile:
    Type: AWS::IAM::InstanceProfile
    Properties:
      Path: /
      Roles:
        - !Ref EC2Role

  # See : https://github.com/aws-cloudformation/aws-cloudformation-templates/blob/main/Solutions/OperatingSystems/RHEL9_cfn-hup.yaml
  EC2Instance:
    CreationPolicy:
      ResourceSignal:
        Count: 1
        Timeout: "PT10M"
    Type: AWS::EC2::Instance
    Metadata:
      AWS::CloudFormation::Init:
        configSets:
          full_install:
            - install_and_enable_cfn_hup
        install_and_enable_cfn_hup:
          files:
            /etc/cfn/cfn-hup.conf:
              content: !Sub |
                [main]
                stack=${AWS::StackId}
                region=${AWS::Region}
              mode: "000400"
              owner: root
              group: root
            /etc/cfn/hooks.d/cfn-auto-reloader.conf:
              content: !Sub |
                [cfn-auto-reloader-hook]
                triggers=post.update
                path=Resources.EC2Instance.Metadata.AWS::CloudFormation::Init
                action=/usr/local/bin/cfn-init -v --stack ${AWS::StackName} --resource EC2Instance --configsets full_install --region ${AWS::Region}
                runas=root
            /lib/systemd/system/cfn-hup.service:
              content: |
                [Unit]
                Description=cfn-hup daemon

                [Service]
                Type=simple
                ExecStart=/usr/local/bin/cfn-hup
                Restart=always

                [Install]
                WantedBy=multi-user.target
          commands:
            01enable_cfn_hup:
              command: systemctl enable cfn-hup.service
            02start_cfn_hup:
              command: systemctl start cfn-hup.service
    Properties:
      ImageId: !Ref EC2AMI
      InstanceType: !FindInMap [EC2DefinitionMap, !Ref EnvType, InstanceType]
      IamInstanceProfile: !Ref IamInstanceProfile
      KeyName: !FindInMap [EC2DefinitionMap, !Ref EnvType, KeyPairName]
      BlockDeviceMappings:
        - DeviceName: /dev/sda1
          Ebs:
            VolumeType: gp2
            VolumeSize: 200
            DeleteOnTermination: true
            Encrypted: false
      NetworkInterfaces:
        - AssociatePublicIpAddress: true
          DeviceIndex: 0
          GroupSet:
            - Fn::ImportValue: !Sub ${VPCName}-SecurityGroupEC2
          SubnetId:
            Fn::ImportValue: !Sub ${VPCName}-PublicSubnet1
      UserData:
        Fn::Base64: !Sub |
          #!/bin/bash -xe
          sudo dnf install -y https://dl.fedoraproject.org/pub/epel/epel-release-latest-9.noarch.rpm
          sudo dnf install -y https://s3.amazonaws.com/ec2-downloads-windows/SSMAgent/latest/linux_amd64/amazon-ssm-agent.rpm
          sudo dnf upgrade -y
          sudo yum update -y
          sudo yum -y install python3-pip snapd
          sudo systemctl enable --now snapd.socket
          sudo ln -s /var/lib/snapd/snap /snap
          sudo pip3 install https://s3.amazonaws.com/cloudformation-examples/aws-cfn-bootstrap-py3-latest.tar.gz
          /usr/local/bin/cfn-init -v --stack ${AWS::StackName} --resource EC2Instance --configsets full_install --region ${AWS::Region}
          /usr/local/bin/cfn-signal -e $? --stack ${AWS::StackName} --resource EC2Instance --region ${AWS::Region}

```

なお、以降、EC2のアクセスはSystems Manager Session Managerより実行する。

**NOTE:** 必要に応じてRHELのサブスクリプション登録を行っておくこと。

```bash
$ subscription-manager register --username xxxxx --password xxxxx --auto-attach
Registering to: subscription.rhsm.redhat.com:443/subscription
The system has been registered with ID: yyyyyy
The registered system name is: ip-zzz-zzz-zzz-zzz.ap-northeast-1.compute.internal
Ignoring the request to auto-attach. Attaching subscriptions is disabled for organization "xxxxxx" because Simple Content Access (SCA) is enabled.
```

----
[index]

2. [Set up MicroK8s](2-set-up-microk8s.md)
3. [Set up Application Environment](3-set-up-app-env.md)
4. [Implementation of Application](4-implementation-app.md)
5. [Deploy Application](5-deploy-app.md)
6. [Implementation of Service Mesh Application](6-implementation-service-mesh-app.md)
7. [Deploy Service Mesh Application](7-deploy-service-mesh-app.md)
