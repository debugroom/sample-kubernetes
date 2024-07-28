
#### アプリケーション環境の構築

- PostgreSQL環境の構築

以下のようなPVCおよびPostgreSQLのサービスのマニフェストを作成し、実行する。

-- postgres-pvc.yaml

```yaml
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: postgres-pvc
spec:
  storageClassName: microk8s-hostpath
  accessModes:
    - "ReadWriteOnce"
  resources:
    requests:
      storage: 1Gi

```

-- postgres.yaml

```yaml
apiVersion: v1
kind: Service
metadata:
  name: postgres
spec:
  type: NodePort
  ports:
    - name: postgres-port
      port: 5432
      targetPort: 5432
      nodePort: 30432
      protocol: TCP
  selector:
    infra: postgres

---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: postgres
spec:
  replicas: 1
  selector:
    matchLabels:
      infra: postgres
  template:
    metadata:
      labels:
        infra: postgres
    spec:
      containers:
        - name: postgres
          image: postgres:11
          env:
            - name: POSTGRES_USER
              value: postgres
            - name: POSTGRES_PASSWORD
              value: postgres
            - name: POSTGRES_DB
              value: sample_database
            - name: PGDATA
              value: /var/lib/postgresql/data/pgdata
          ports:
            - containerPort: 5432
          volumeMounts:
            - mountPath: /var/lib/postgresql/data
              name: pg-data
      volumes:
        - name: pg-data
          persistentVolumeClaim:
            claimName: postgres-pvc

```

```bash
$ microk8s kubectl apply -f postgres-pvc.yml
persistentvolumeclaim/postgres-pvc created

$ microk8s kubectl apply -f postgres.yml
service/postgres created
deployment.apps/postgres created

$ microk8s kubectl describe pvc postgres-pvc
Name:          postgres-pvc
Namespace:     default
StorageClass:  microk8s-hostpath
Status:        Bound
Volume:        pvc-b083660c-2181-408b-9e85-aea61812e53e
Labels:        <none>
Annotations:   pv.kubernetes.io/bind-completed: yes
               pv.kubernetes.io/bound-by-controller: yes
               volume.beta.kubernetes.io/storage-provisioner: microk8s.io/hostpath
               volume.kubernetes.io/selected-node: ip-172-7-1-239.ap-northeast-1.compute.internal
               volume.kubernetes.io/storage-provisioner: microk8s.io/hostpath
Finalizers:    [kubernetes.io/pvc-protection]
Capacity:      1Gi
Access Modes:  RWO
VolumeMode:    Filesystem
Used By:       postgres-7f8c6b8ddf-9j6tx
Events:
  Type    Reason                 Age                     From                                                                                             Message
  ----    ------                 ----                    ----                                                                                             -------
  Normal  WaitForFirstConsumer   2m19s (x24 over 7m52s)  persistentvolume-controller                                                                      waiting for firstconsumer to be created before binding
  Normal  ExternalProvisioning   2m4s (x2 over 2m4s)     persistentvolume-controller                                                                      Waiting for a volume to be created either by the external provisioner 'microk8s.io/hostpath' or manually by the system administrator. If volume creation is delayed, please verify that the provisioner is running and correctly registered.
  Normal  Provisioning           2m4s                    microk8s.io/hostpath_hostpath-provisioner-7c8bdf94b8-s4b45_b7098777-e57b-4ab6-be2c-e8683cd98739  External provisioner is provisioning volume for claim "default/postgres-pvc"
  Normal  ProvisioningSucceeded  114s                    microk8s.io/hostpath_hostpath-provisioner-7c8bdf94b8-s4b45_b7098777-e57b-4ab6-be2c-e8683cd98739  Successfully provisioned volume pvc-b083660c-2181-408b-9e85-aea61812e53e

$ microk8s kubectl describe deployment postgres
  Name:                   postgres
  Namespace:              default
  CreationTimestamp:      Sat, 27 Jul 2024 18:11:08 +0000
  Labels:                 <none>
  Annotations:            deployment.kubernetes.io/revision: 1
  Selector:               infra=postgres
  Replicas:               1 desired | 1 updated | 1 total | 1 available | 0 unavailable
  StrategyType:           RollingUpdate
  MinReadySeconds:        0
  RollingUpdateStrategy:  25% max unavailable, 25% max surge
  Pod Template:
    Labels:  infra=postgres
    Containers:
     postgres:
      Image:      postgres:11
      Port:       5432/TCP
      Host Port:  0/TCP
      Environment:
        POSTGRES_USER:      postgres
        POSTGRES_PASSWORD:  postgres
        POSTGRES_DB:        sample_database
        PGDATA:             /var/lib/postgresql/data/pgdata
      Mounts:
        /var/lib/postgresql/data from pg-data (rw)
    Volumes:
     pg-data:
      Type:          PersistentVolumeClaim (a reference to a PersistentVolumeClaim in the same namespace)
      ClaimName:     postgres-pvc
      ReadOnly:      false
    Node-Selectors:  <none>
    Tolerations:     <none>
  Conditions:
    Type           Status  Reason
    ----           ------  ------
    Available      True    MinimumReplicasAvailable
    Progressing    True    NewReplicaSetAvailable
  OldReplicaSets:  <none>
  NewReplicaSet:   postgres-7f8c6b8ddf (1/1 replicas created)
  Events:
    Type    Reason             Age   From                   Message
    ----    ------             ----  ----                   -------
    Normal  ScalingReplicaSet  5m4s  deployment-controller  Scaled up replica set postgres-7f8c6b8ddf to 1

$ export postgres_pod_name=`microk8s kubectl get pods -o custom-columns=:.metadata.name -l infra=postgres`
$ microk8s kubectl exec $postgres_pod_name -- psql -U postgres -d sample_database -c "select * from information_schema.tables;"

```

**NOTE:** データベース作成後は以下のコマンドでSQLを発行することができる。

```bash
$ export postgres_pod_name=`microk8s kubectl get pods -o custom-columns=:.metadata.name -l infra=postgres`
$ microk8s kubectl exec $postgres_pod_name -- psql -U postgres -d sample_database -c "select * from information_schema.tables;"

```
