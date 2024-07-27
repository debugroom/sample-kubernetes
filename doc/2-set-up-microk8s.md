
#### MicroK8sのインストール

- MicroK8sのインストール

snapを使用して、microk8sをインストールする。
See : https://microk8s.io/docs/getting-started

```bash
$ snap version
snap    2.63-0.el9
snapd   2.63-0.el9
series  16
rhel    9.4
kernel  5.14.0-427.20.1.el9_4.x86_64

$ sudo snap install microk8s --classic --channel=1.30
2024-07-24T22:42:18Z INFO Waiting for automatic snapd restart...
microk8s (1.30/stable) v1.30.1 from Canonical✓ installed

$ export USER=`whoami`
$ sudo usermod -a -G microk8s $USER
$ mkdir -p ~/.kube
$ chmod 0700 ~/.kube

```

- MicroK8sの起動確認

一度、Session Managerからログアウトし、再びセッションを開始し、MicroK8sの起動ステータスを確認する。

```bash
$ microk8s status --wait-ready
2024/07/24 22:53:42.245755 cmd_run.go:442: restoring default SELinux context of /home/ssm-user/snap
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
  disabled:
    cert-manager         # (core) Cloud native certificate management
    cis-hardening        # (core) Apply CIS K8s hardening
    community            # (core) The community addons repository
    dashboard            # (core) The Kubernetes dashboard
    gpu                  # (core) Alias to nvidia add-on
    host-access          # (core) Allow Pods connecting to Host services smoothly
    hostpath-storage     # (core) Storage class; allocates storage from host directory
    ingress              # (core) Ingress controller for external access
    kube-ovn             # (core) An advanced network fabric for Kubernetes
    mayastor             # (core) OpenEBS MayaStor
    metallb              # (core) Loadbalancer for your Kubernetes cluster
    metrics-server       # (core) K8s Metrics Server for API access to service metrics
    minio                # (core) MinIO object storage
    nvidia               # (core) NVIDIA hardware (GPU and network) support
    observability        # (core) A lightweight observability stack for logs, traces and metrics
    prometheus           # (core) Prometheus operator for monitoring and logging
    rbac                 # (core) Role-Based Access Control for authorisation
    registry             # (core) Private image registry exposed on localhost:32000
    rook-ceph            # (core) Distributed Ceph storage using Rook
    storage              # (core) Alias to hostpath-storage add-on, deprecated

$ microk8s kubectl get all --all-namespaces

    NAMESPACE     NAME                                          READY   STATUS    RESTARTS   AGE
    kube-system   pod/calico-kube-controllers-796fb75cc-lh9kb   1/1     Running   0          42m
    kube-system   pod/calico-node-cqfrg                         1/1     Running   0          42m
    kube-system   pod/coredns-5986966c54-2fm58                  1/1     Running   0          42m

    NAMESPACE     NAME                 TYPE        CLUSTER-IP      EXTERNAL-IP   PORT(S)                  AGE
    default       service/kubernetes   ClusterIP   10.152.183.1    <none>        443/TCP                  42m
    kube-system   service/kube-dns     ClusterIP   10.152.183.10   <none>        53/UDP,53/TCP,9153/TCP   42m

    NAMESPACE     NAME                         DESIRED   CURRENT   READY   UP-TO-DATE   AVAILABLE   NODE SELECTOR            AGE
    kube-system   daemonset.apps/calico-node   1         1         1       1            1           kubernetes.io/os=linux   42m

    NAMESPACE     NAME                                      READY   UP-TO-DATE   AVAILABLE   AGE
    kube-system   deployment.apps/calico-kube-controllers   1/1     1            1           42m
    kube-system   deployment.apps/coredns                   1/1     1            1           42m

    NAMESPACE     NAME                                                DESIRED   CURRENT   READY   AGE
    kube-system   replicaset.apps/calico-kube-controllers-796fb75cc   1         1         1       42m
    kube-system   replicaset.apps/coredns-5986966c54                  1         1         1       42m

```

- アドオンの有効化

ストレージを有効化する。

```bash
$ microk8s enable hostpath-storage

Infer repository core for addon hostpath-storage
Enabling default storage class.
WARNING: Hostpath storage is not suitable for production environments.
         A hostpath volume can grow beyond the size limit set in the volume claim manifest.

deployment.apps/hostpath-provisioner created
storageclass.storage.k8s.io/microk8s-hostpath created
serviceaccount/microk8s-hostpath created
clusterrole.rbac.authorization.k8s.io/microk8s-hostpath created
clusterrolebinding.rbac.authorization.k8s.io/microk8s-hostpath created
Storage will be available soon.
```

- Chartmuseumのインストール

アプリケーションのコンテナレジストリ用途としてChartmeseumを利用する。ChartMuseumをローカルにインストールする。
See : https://github.com/helm/chartmuseum

```bash
$ curl https://raw.githubusercontent.com/helm/chartmuseum/main/scripts/get-chartmuseum | bash
  % Total    % Received % Xferd  Average Speed   Time    Time     Time  Current
                                 Dload  Upload   Total   Spent    Left  Speed
100 11635  100 11635    0     0  39574      0 --:--:-- --:--:-- --:--:-- 39574
Downloading https://get.helm.sh/chartmuseum-v0.16.2-linux-amd64.tar.gz
Verifying checksum... Done.
Preparing to install chartmuseum into /usr/local/bin
chartmuseum installed into /usr/local/bin/chartmuseum

$ mkdir ~/chartstorage

```

- Chartmuseumの起動

Chartmuseumをポート8000番で起動する。アプリケーションをHelm-Pushする際は下記のプロセスで受け付けるよう設定する。

```bash
$ chartmuseum --debug --port=8000 --storage="local" --storage-local-rootdir="~/chartstorage" --basic-auth-user=debugroom--basic-auth-pass=debugroom --auth-anonymous-get --allow-overwrite &
[1] 1719737
2024-07-27T15:39:54.686Z   DEBUG   Fetching chart list from storage        {"repo": ""}
2024-07-27T15:39:54.686Z        DEBUG   No change detected between cache and storage    {"repo": ""}
2024-07-27T15:39:54.686Z        INFO    Starting ChartMuseum    {"host": "0.0.0.0", "port": 8000}
2024-07-27T15:39:54.686Z        DEBUG   Starting internal event listener

```

**NOTE:** アプリケーションは開発端末からコンテナイメージを作成し、Helmにプッシュする。EC2インスタンスのインバウンドルールで、開発端末から8000番のアクセスを許可しておくこと(セキュリティ上、IaCコードには書かずAWSコンソール上から下記の設定する)。

```yaml
SecurityGroupIngressEC2:
  Type: AWS::EC2::SecurityGroupIngress
  Properties:
    GroupId: !Ref SecurityGroupEC2
    IpProtocol: tcp
    FromPort: 8000
    ToPort: 8000
    CidrIp: XXX.XXX.XXX.XXX/32
```
