
#### サービスメッシュアプリケーションのデプロイ

**TODO:**

- Istioの有効化
- ネームスペースの作成
- サービスメッシュの構築
- アプリケーションのデプロイ

<!--
MicroK8sでLoadbalancerであるMetalLBを有効化する。

```bash
$ microk8s enable metallb

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
$ microk8s helm3 repo update
$ microk8s helm3 search repo chartmuseum/ --devel
NAME                                            CHART VERSION   APP VERSION     DESCRIPTION
chartmuseum/sample-kubernetes-dbaccess-app      0.0.1-SNAPSHOT                  Demo project for Spring Boot

$ microk8s helm3 install sample-kuberntes-dbaccess-app chartmuseum/sample-kubernetes-dbaccess-app --devel
NAME: sample-kuberntes-dbaccess-app
LAST DEPLOYED: Sun Jul 28 08:10:19 2024
NAMESPACE: default
STATUS: deployed
REVISION: 1
TEST SUITE: None

$ microk8s kubectl describe deployment sample-kubernetes-dbaccess-app
Name:                   sample-kubernetes-dbaccess-app
Namespace:              default
CreationTimestamp:      Sun, 28 Jul 2024 08:10:19 +0000
Labels:                 app=sample-kubernetes-dbaccess-app
                        app.kubernetes.io/managed-by=Helm
                        group=org.debugroom
                        provider=jkube
                        version=0.0.1-SNAPSHOT
Annotations:            deployment.kubernetes.io/revision: 1
                        jkube.io/git-branch: master
                        jkube.io/git-commit: fccd204f4db149aaa032a820c6956fe5f5b0562c
                        jkube.io/git-url: https://github.com/debugroom/sample-kubernetes.git
                        jkube.io/scm-tag: HEAD
                        jkube.io/scm-url: https://github.com/spring-projects/spring-boot/sample-kubernetes-dbaccess-app
                        meta.helm.sh/release-name: sample-kuberntes-dbaccess-app
                        meta.helm.sh/release-namespace: default
Selector:               app=sample-kubernetes-dbaccess-app,group=org.debugroom,provider=jkube
Replicas:               1 desired | 1 updated | 1 total | 1 available | 0 unavailable
StrategyType:           RollingUpdate
MinReadySeconds:        0
RollingUpdateStrategy:  25% max unavailable, 25% max surge
Pod Template:
  Labels:       app=sample-kubernetes-dbaccess-app
                group=org.debugroom
                provider=jkube
                version=0.0.1-SNAPSHOT
  Annotations:  jkube.io/git-branch: master
                jkube.io/git-commit: fccd204f4db149aaa032a820c6956fe5f5b0562c
                jkube.io/git-url: https://github.com/debugroom/sample-kubernetes.git
                jkube.io/scm-tag: HEAD
                jkube.io/scm-url: https://github.com/spring-projects/spring-boot/sample-kubernetes-dbaccess-app
  Containers:
   spring-boot:
    Image:       debugroom/sample-kubernetes-dbaccess-app:latest
    Ports:       8080/TCP, 9779/TCP, 8778/TCP
    Host Ports:  0/TCP, 0/TCP, 0/TCP
    Environment:
      DB_URL:                  postgres:5432
      SPRING_PROFILES_ACTIVE:  dev
      KUBERNETES_NAMESPACE:     (v1:metadata.namespace)
      HOSTNAME:                 (v1:metadata.name)
    Mounts:                    <none>
  Volumes:                     <none>
  Node-Selectors:              <none>
  Tolerations:                 <none>
Conditions:
  Type           Status  Reason
  ----           ------  ------
  Available      True    MinimumReplicasAvailable
  Progressing    True    NewReplicaSetAvailable
OldReplicaSets:  <none>
NewReplicaSet:   sample-kubernetes-dbaccess-app-6f6dc66cb7 (1/1 replicas created)
Events:
  Type    Reason             Age   From                   Message
  ----    ------             ----  ----                   -------
  Normal  ScalingReplicaSet  104s  deployment-controller  Scaled up replica set sample-kubernetes-dbaccess-app-6f6dc66cb7 to 1

$ microk8s kubectl expose deployment sample-kubernetes-dbaccess-app --type=LoadBalancer --port=8080 --name=sample-kubernetes-dbaccess-app-lb
service/sample-kubernetes-dbaccess-app exposed

$ microk8s kubectl get services
NAME                                TYPE           CLUSTER-IP       EXTERNAL-IP   PORT(S)          AGE
kubernetes                          ClusterIP      10.152.183.1     <none>        443/TCP          3d10h
postgres                            NodePort       10.152.183.56    <none>        5432:30432/TCP   15h
sample-kubernetes-dbaccess-app      ClusterIP      10.152.183.180   <none>        8080/TCP         66m
sample-kubernetes-dbaccess-app-lb   LoadBalancer   10.152.183.196   192.168.1.0   8080:31570/TCP   23s
```

- アプリケーションへのアクセス確認

ロードバランサーのEXTERNAL-IPおよびデプロイしたポート番号を指定してアクセスする。

```bash
$ curl http://192.168.1.0:8080/sample-api/samples

[{"text":"0"}]

$ curl http://192.168.1.0:8080/sample-api/users/0

{"userId":"0","firstName":"taro","familyName":"mynavi","loginId":"taro.mynavi","imageFilePath":"taro.png","credentialResources":[{"userId":0,"credentialType":"PASSWORD","credentialKey":"$2a$11$5knhINqfA8BgXY1Xkvdhvu0kOhdlAeN1H/TlJbTbuUPDdqq.H.zzi","validDate":"2019-01-01T00:00:00.000+00:00"},{"userId":0,"credentialType":"ACCESSTOKEN","credentialKey":"987654321poiuytrewq","validDate":"2016-01-01T00:00:00.000+00:00"}],"admin":true,"login":false}

```

-->
