
#### アプリケーションのデプロイ

- MetalLBの有効化

MicroK8sでLoadbalancerであるMetalLBを有効化する。

```bash
$ microk8s enable metallb:192.168.1.240/24

Infer repository core for addon metallb
Enabling MetalLB
Applying Metallb manifest
customresourcedefinition.apiextensions.k8s.io/addresspools.metallb.io created
customresourcedefinition.apiextensions.k8s.io/bfdprofiles.metallb.io created
customresourcedefinition.apiextensions.k8s.io/bgpadvertisements.metallb.io created
customresourcedefinition.apiextensions.k8s.io/bgppeers.metallb.io created
customresourcedefinition.apiextensions.k8s.io/communities.metallb.io created
customresourcedefinition.apiextensions.k8s.io/ipaddresspools.metallb.io created
customresourcedefinition.apiextensions.k8s.io/l2advertisements.metallb.io created
namespace/metallb-system created
serviceaccount/controller created
serviceaccount/speaker created
clusterrole.rbac.authorization.k8s.io/metallb-system:controller created
clusterrole.rbac.authorization.k8s.io/metallb-system:speaker created
role.rbac.authorization.k8s.io/controller created
role.rbac.authorization.k8s.io/pod-lister created
clusterrolebinding.rbac.authorization.k8s.io/metallb-system:controller created
clusterrolebinding.rbac.authorization.k8s.io/metallb-system:speaker created
rolebinding.rbac.authorization.k8s.io/controller created
secret/webhook-server-cert created
service/webhook-service created
rolebinding.rbac.authorization.k8s.io/pod-lister created
daemonset.apps/speaker created
deployment.apps/controller created
validatingwebhookconfiguration.admissionregistration.k8s.io/validating-webhook-configuration created
Waiting for Metallb controller to be ready.

deployment.apps/controller condition met
ipaddresspool.metallb.io/default-addresspool created
l2advertisement.metallb.io/default-advertise-all-pools created
MetalLB is enabled

$ microk8s status --wait-ready
microk8s is running
high-availability: no
  datastore master nodes: 127.0.0.1:19001
  datastore standby nodes: none
addons:
  enabled:
    dns                  # (core) CoreDNS
    ha-cluster           # (core) Configure high availability on the current node
    helm                 # (core) Helm - the package manager for Kubernetes
    helm3                # (core) Helm 3 - the package manager for Kubernetes
    hostpath-storage     # (core) Storage class; allocates storage from host directory
    metallb              # (core) Loadbalancer for your Kubernetes cluster
    storage              # (core) Alias to hostpath-storage add-on, deprecated
  disabled:
    cert-manager         # (core) Cloud native certificate management
    cis-hardening        # (core) Apply CIS K8s hardening
    community            # (core) The community addons repository
    dashboard            # (core) The Kubernetes dashboard
    gpu                  # (core) Alias to nvidia add-on
    host-access          # (core) Allow Pods connecting to Host services smoothly
    ingress              # (core) Ingress controller for external access
    kube-ovn             # (core) An advanced network fabric for Kubernetes
    mayastor             # (core) OpenEBS MayaStor
    metrics-server       # (core) K8s Metrics Server for API access to service metrics
    minio                # (core) MinIO object storage
    nvidia               # (core) NVIDIA hardware (GPU and network) support
    observability        # (core) A lightweight observability stack for logs, traces and metrics
    prometheus           # (core) Prometheus operator for monitoring and logging
    rbac                 # (core) Role-Based Access Control for authorisation
    registry             # (core) Private image registry exposed on localhost:32000
    rook-ceph            # (core) Distributed Ceph storage using Rook

```

- アプリケーションのデプロイメント

ChartMuseumにPushしたコンテナイメージをデプロイする。

```bash
$ microk8s helm3 search repo chartmuseum/ --devel
NAME                                            CHART VERSION   APP VERSION     DESCRIPTION
chartmuseum/sample-kubernetes-dbaccess-app      0.0.1-SNAPSHOT                  Demo project for Spring Boot

$ microk8s kubectl create deployment sample-kubernetes-dbaccess-app --image=debugroom/sample-kubernetes-dbaccess-app:latest
deployment.apps/sample-kubernetes-dbaccess-app created

$ microk8s kubectl describe deployment sample-kubernetes-dbaccess-app
Name:                   sample-kubernetes-dbaccess-app
Namespace:              default
CreationTimestamp:      Sat, 27 Jul 2024 21:06:26 +0000
Labels:                 app=sample-kubernetes-dbaccess-app
Annotations:            deployment.kubernetes.io/revision: 1
Selector:               app=sample-kubernetes-dbaccess-app
Replicas:               1 desired | 1 updated | 1 total | 1 available | 0 unavailable
StrategyType:           RollingUpdate
MinReadySeconds:        0
RollingUpdateStrategy:  25% max unavailable, 25% max surge
Pod Template:
  Labels:  app=sample-kubernetes-dbaccess-app
  Containers:
   sample-kubernetes-dbaccess-app:
    Image:         debugroom/sample-kubernetes-dbaccess-app:latest
    Port:          <none>
    Host Port:     <none>
    Environment:   <none>
    Mounts:        <none>
  Volumes:         <none>
  Node-Selectors:  <none>
  Tolerations:     <none>
Conditions:
  Type           Status  Reason
  ----           ------  ------
  Available      True    MinimumReplicasAvailable
  Progressing    True    NewReplicaSetAvailable
OldReplicaSets:  <none>
NewReplicaSet:   sample-kubernetes-dbaccess-app-79c7689b48 (1/1 replicas created)
Events:
  Type    Reason             Age   From                   Message
  ----    ------             ----  ----                   -------
  Normal  ScalingReplicaSet  108s  deployment-controller  Scaled up replica set sample-kubernetes-dbaccess-app-79c7689b48 to 1

$ microk8s kubectl expose deployment sample-kubernetes-dbaccess-app --type=LoadBalancer --port=8080
service/sample-kubernetes-dbaccess-app exposed

$ microk8s kubectl get services
NAME                             TYPE           CLUSTER-IP      EXTERNAL-IP   PORT(S)          AGE
kubernetes                       ClusterIP      10.152.183.1    <none>        443/TCP          2d22h
postgres                         NodePort       10.152.183.56   <none>        5432:30432/TCP   3h3m
sample-kubernetes-dbaccess-app   LoadBalancer   10.152.183.18   192.168.1.0   8080:31305/TCP   6m18s

```

- アプリケーションへのアクセス確認

ロードバランサーのEXTERNAL-IPおよびデプロイしたポート番号を指定してアクセスする。

```bash
$ curl http://192.168.1.0:8080/sample-api/samples

[{"text":"0"}]

$ curl http://192.168.1.0:8080/sample-api/users/0

{"userId":"0","firstName":"taro","familyName":"mynavi","loginId":"taro.mynavi","imageFilePath":"taro.png","credentialResources":[{"userId":0,"credentialType":"PASSWORD","credentialKey":"$2a$11$5knhINqfA8BgXY1Xkvdhvu0kOhdlAeN1H/TlJbTbuUPDdqq.H.zzi","validDate":"2019-01-01T00:00:00.000+00:00"},{"userId":0,"credentialType":"ACCESSTOKEN","credentialKey":"987654321poiuytrewq","validDate":"2016-01-01T00:00:00.000+00:00"}],"admin":true,"login":false}

```
