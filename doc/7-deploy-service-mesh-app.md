
#### サービスメッシュアプリケーションのデプロイ

- Istioのインストール・有効化

Helmを使って、Istioをカスタムインストールする。以下の通り、istio環境全体のプロジェクトを作成する。なお、templatesディレクトリに作成されるスケルトンテンプレート等は削除しておく。
See: https://istio.io/latest/docs/setup/install/helm/

```bash
$ helm3 create istio-microk8s
Creating istio-microk8s

$ cd istio-microk8s/
$ ls
Chart.yaml  charts  templates  values.yaml

$ rm -rf templates/*
```

以下の通り、Chart.yamlを作成する。

```yaml
apiVersion: v2
name: istio-microk8s
description: A Helm chart for Kubernetes

# A chart can be either an 'application' or a 'library' chart.
#
# Application charts are a collection of templates that can be packaged into versioned archives
# to be deployed.
#
# Library charts provide useful utilities or functions for the chart developer. They're included as
# a dependency of application charts to inject those utilities and functions into the rendering
# pipeline. Library charts do not define any templates and therefore cannot be deployed.
type: application

# This is the chart version. This version number should be incremented each time you make changes
# to the chart and its templates, including the app version.
version: 0.1.0

# This is the version number of the application being deployed. This version number should be
# incremented each time you make changes to the application.
appVersion: 1.16.0

### Add istio-custom helm installation definition.

dependencies:
  - name: istio-init
    version: 0.1
    condition: istio-init.enabled

  - name: base
    repository: https://istio-release.storage.googleapis.com/charts
    version: 1.23.0
    condition: istio-base.enabled
    tags:
      - istio-base
  - name: istiod
    repository: https://istio-release.storage.googleapis.com/charts
    version: 1.23.0
    condition: istiod.enabled
    tags:
      - istiod
  - name: gateway
    repository: https://istio-release.storage.googleapis.com/charts
    version: 1.23.0
    condition: istio-ingressgateway-enabled
    alias: istio-ingressgateway
    tags:
      - istio-ingressgateway
  - name: gateway
    repository: https://istio-release.storage.googleapis.com/charts
    version: 1.23.0
    condition: istio-egressgateway-enabled
    alias: istio-egressgateway
    tags:
      - istio.egressgateway

#  - name: istio-routing-setup
#    version: 0.1
#    condition: istio-routing-setup.enabled
#
#  - name: istio-observability-prometheus
#    version: 0.1
#    condition: istio-observability-prometheus.enabled
#
#  - name: istio-observability-kiali
#    version: 0.1
#    condition: istio-observability-kiali.enabled
```

Istio初期化用のChart テンプレートを作成する。

```bash
$ cd charts/
$ helm3 create istio-init
Creating istio-init

$ rm -rf istio-init/templates/*
$ cd ..
$ ls
Chart.yaml  charts  templates  values.yaml
```

依存関係を更新して、チャートを取得する。

```
$ helm3 dependency update

Hang tight while we grab the latest from your chart repositories...
...Successfully got an update from the "eks" chart repository
Update Complete. ⎈Happy Helming!⎈
Saving 5 charts
Dependency istio-init did not declare a repository. Assuming it exists in the charts directory
Downloading base from repo https://istio-release.storage.googleapis.com/charts
Downloading istiod from repo https://istio-release.storage.googleapis.com/charts
Downloading gateway from repo https://istio-release.storage.googleapis.com/charts
Downloading gateway from repo https://istio-release.storage.googleapis.com/charts
Deleting outdated charts

```

カスタム用にHelm インストール時のパラメータ変数の置き換えでvalues.yamlを以下の通り作成しておく。

```yaml
# Default values for istio-microk8s.
# This is a YAML-formatted file.
# Declare variables to be passed into your templates.

istio-init:
  enabled: false

istio-base:
  enabled: false

istiod:
  enabled: false
  meshConfig:
    enablePrometheusMerge: true
    # Config for the default ProxyConfig.
    # Initially using directly the proxy metadata - can also be activated using annotations
    # on the pod. This is an unsupported low-level API, pending review and decisions on
    # enabling the feature. Enabling the DNS listener is safe - and allows further testing
    # and gradual adoption by setting capture only on specific workloads. It also allows
    # VMs to use other DNS options, like dnsmasq or unbound.

    # The namespace to treat as the administrative root namespace for Istio configuration.
    # When processing a leaf namespace Istio will search for declarations in that namespace first
    # and if none are found it will search in the root namespace. Any matching declaration found in the root namespace
    # is processed as if it were declared in the leaf namespace.

    rootNamespace: istio-system

    # The trust domain corresponds to the trust root of a system
    # Refer to https://github.com/spiffe/spiffe/blob/master/standards/SPIFFE-ID.md#21-trust-domain
    trustDomain: "cluster.local"

    # TODO: the intent is to eventually have this enabled by default when security is used.
    # It is not clear if user should normally need to configure - the metadata is typically
    # used as an escape and to control testing and rollout, but it is not intended as a long-term
    # stable API.

    # What we may configure in mesh config is the ".global" - and use of other suffixes.
    # No hurry to do this in 1.6, we're trying to prove the code.

istio-ingressgateway-enabled: false
istio-ingressgateway:
  name: istio-ingressgateway
  labels:
    app: istio-ingressgateway
    istio: ingressgateway
  service:
# For AWS ALB
#    annotations:
#      service.beta.kubernetes.io/aws-load-balancer-name: sample-ingress
#      service.beta.kubernetes.io/aws-load-balancer-type: "external"
#      service.beta.kubernetes.io/aws-load-balancer-nlb-target-type: "ip"
#      #      service.beta.kubernetes.io/aws-load-balancer-scheme: "internal"
#      service.beta.kubernetes.io/aws-load-balancer-scheme: "internet-facing"

istio-egressgateway-enabled: false
istio-egressgateway:
  name: istio-egressgateway
  labels:
    app: istio-egressgateway
    istio: egressgateway
  service:
    type: ClusterIP

istio-routing-setup:
  enabled: false

istio-observability-prometheus:
  enabled: false

istio-observability-kiali:
  enabled: false

```


- ネームスペースの作成

See: https://istio.io/latest/docs/setup/install/helm/#installation-steps

事前の初期処理として、istio自体のネームスペースとサービスメッシュを適用するアプリケーション向けのネームスペースを作成する。

```yaml
apiVersion: v1
kind: Namespace
metadata:
  name: istio-system
---
apiVersion: v1
kind: Namespace
metadata:
  name: istio-network
  labels:
    istio-injection: enabled
```

```bash
$ helm3 template . --set istio-init.enabled=true | microk8s kubectl apply -f -
$ microk8s helm3 template . --set istio-init.enabled=true | microk8s kubectl apply -f -

Warning: resource namespaces/istio-system is missing the kubectl.kubernetes.io/last-applied-configuration annotation which is required by kubectl apply. kubectl apply should only be used on resources created declaratively by either kubectl create --save-config or kubectl apply. The missing annotation will be patched automatically.
namespace/istio-system configured
namespace/istio-network created
```

- サービスメッシュの構築

Istio baseをインストールする。

```bash
$ microk8s helm3 template release-$(date '+%Y%m%d') . --set istio-base.enabled=true --set base.base.enableCRDTemplates=true --set defaultRevision=default -n istio-system | microk8s kubectl apply -f -

serviceaccount/istio-reader-service-account created
validatingwebhookconfiguration.admissionregistration.k8s.io/istiod-default-validator created
customresourcedefinition.apiextensions.k8s.io/wasmplugins.extensions.istio.io created
customresourcedefinition.apiextensions.k8s.io/destinationrules.networking.istio.io created
customresourcedefinition.apiextensions.k8s.io/envoyfilters.networking.istio.io created
customresourcedefinition.apiextensions.k8s.io/gateways.networking.istio.io created
customresourcedefinition.apiextensions.k8s.io/proxyconfigs.networking.istio.io created
customresourcedefinition.apiextensions.k8s.io/serviceentries.networking.istio.io created
customresourcedefinition.apiextensions.k8s.io/sidecars.networking.istio.io created
customresourcedefinition.apiextensions.k8s.io/virtualservices.networking.istio.io created
customresourcedefinition.apiextensions.k8s.io/workloadentries.networking.istio.io created
customresourcedefinition.apiextensions.k8s.io/workloadgroups.networking.istio.io created
customresourcedefinition.apiextensions.k8s.io/authorizationpolicies.security.istio.io created
customresourcedefinition.apiextensions.k8s.io/peerauthentications.security.istio.io created
customresourcedefinition.apiextensions.k8s.io/requestauthentications.security.istio.io created
customresourcedefinition.apiextensions.k8s.io/telemetries.telemetry.istio.io created
validatingwebhookconfiguration.admissionregistration.k8s.io/istiod-default-validator configured

```

**NOTE:** 上記のコマンド実行時には、以下のyamlテンプレート(v1.23.0)が適用される(CRDは除く)。

```yaml
---
# Source: istio-microk8s/charts/base/templates/reader-serviceaccount.yaml
# This service account aggregates reader permissions for the revisions in a given cluster
# Should be used for remote secret creation.
apiVersion: v1
kind: ServiceAccount
metadata:
  name: istio-reader-service-account
  namespace: istio-system
  labels:
    app: istio-reader
    release: release-20240820
---
# Source: istio-microk8s/charts/base/templates/zzz_profile.yaml
#  Flatten globals, if defined on a per-chart basis
---
# Source: istio-microk8s/charts/base/templates/default.yaml
apiVersion: admissionregistration.k8s.io/v1
kind: ValidatingWebhookConfiguration
metadata:
  name: istiod-default-validator
  labels:
    app: istiod
    release: release-20240820
    istio: istiod
    istio.io/rev: "default"
webhooks:
  - name: validation.istio.io
    clientConfig:
      service:
        name: istiod
        namespace: istio-system
        path: "/validate"
    rules:
      - operations:
          - CREATE
          - UPDATE
        apiGroups:
          - security.istio.io
          - networking.istio.io
          - telemetry.istio.io
          - extensions.istio.io
        apiVersions:
          - "*"
        resources:
          - "*"
    # Fail open until the validation webhook is ready. The webhook controller
    # will update this to `Fail` and patch in the `caBundle` when the webhook
    # endpoint is ready.
    failurePolicy: Ignore
    sideEffects: None
    admissionReviewVersions: ["v1"]

```

<details><summary>CRD定義含むテンプレート(長すぎるため中略)</summary>

```
---
# Source: istio-microk8s/charts/base/templates/reader-serviceaccount.yaml
# This service account aggregates reader permissions for the revisions in a given cluster
# Should be used for remote secret creation.
apiVersion: v1
kind: ServiceAccount
metadata:
  name: istio-reader-service-account
  namespace: istio-system
  labels:
    app: istio-reader
    release: release-20240822
---
# Source: istio-microk8s/charts/base/templates/crds.yaml
# DO NOT EDIT - Generated by Cue OpenAPI generator based on Istio APIs.
apiVersion: apiextensions.k8s.io/v1
kind: CustomResourceDefinition
metadata:
  annotations:
    "helm.sh/resource-policy": keep
  labels:
    app: istio-pilot
    chart: istio
    heritage: Tiller
    release: istio
  name: wasmplugins.extensions.istio.io
spec:
  group: extensions.istio.io
  names:
    categories:
    - istio-io
    - extensions-istio-io
    kind: WasmPlugin
    listKind: WasmPluginList
    plural: wasmplugins
    singular: wasmplugin
  scope: Namespaced
  versions:
  - additionalPrinterColumns:
    - description: 'CreationTimestamp is a timestamp representing the server time
        when this object was created. It is not guaranteed to be set in happens-before
        order across separate operations. Clients may not set this value. It is represented
        in RFC3339 form and is in UTC. Populated by the system. Read-only. Null for
        lists. More info: https://git.k8s.io/community/contributors/devel/api-conventions.md#metadata'
      jsonPath: .metadata.creationTimestamp
      name: Age
      type: date
    name: v1alpha1
    schema:
      openAPIV3Schema:
        properties:
          spec:
            description: 'Extend the functionality provided by the Istio proxy through
              WebAssembly filters. See more details at: https://istio.io/docs/reference/config/proxy_extensions/wasm-plugin.html'
            properties:
              failStrategy:
                description: |-
                  Specifies the failure behavior for the plugin due to fatal errors.

                  Valid Options: FAIL_CLOSE, FAIL_OPEN
                enum:
                - FAIL_CLOSE
                - FAIL_OPEN
                type: string
              imagePullPolicy:
                description: |-
                  The pull behaviour to be applied when fetching Wasm module by either OCI image or `http/https`.

                  Valid Options: IfNotPresent, Always
                enum:
                - UNSPECIFIED_POLICY
                - IfNotPresent
                - Always
                type: string
              imagePullSecret:
                description: Credentials to use for OCI image pulling.
                maxLength: 253
                minLength: 1
                type: string
              match:
                description: Specifies the criteria to determine which traffic is
                  passed to WasmPlugin.
                items:
                  properties:
                    mode:
                      description: |-
                        Criteria for selecting traffic by their direction.

                        Valid Options: CLIENT, SERVER, CLIENT_AND_SERVER
                      enum:
                      - UNDEFINED
                      - CLIENT
                      - SERVER
                      - CLIENT_AND_SERVER
                      type: string
                    ports:
                      description: Criteria for selecting traffic by their destination
                        port.
                      items:
                        properties:
                          number:
                            maximum: 65535
                            minimum: 1
                            type: integer
                        required:
                        - number
                        type: object
                      type: array
                      x-kubernetes-list-map-keys:
                      - number
                      x-kubernetes-list-type: map
                  type: object
                type: array
              phase:
                description: |-
                  Determines where in the filter chain this `WasmPlugin` is to be injected.

                  Valid Options: AUTHN, AUTHZ, STATS
                enum:
                - UNSPECIFIED_PHASE
                - AUTHN
                - AUTHZ
                - STATS
                type: string
              pluginConfig:
                description: The configuration that will be passed on to the plugin.
                type: object
                x-kubernetes-preserve-unknown-fields: true
              pluginName:
                description: The plugin name to be used in the Envoy configuration
                  (used to be called `rootID`).
                maxLength: 256
                minLength: 1
                type: string
              priority:
                description: Determines ordering of `WasmPlugins` in the same `phase`.
                format: int32
                nullable: true
                type: integer
              selector:
                description: Criteria used to select the specific set of pods/VMs
                  on which this plugin configuration should be applied.
                properties:
                  matchLabels:
                    additionalProperties:
                      maxLength: 63
                      type: string
                      x-kubernetes-validations:
                      - message: wildcard not allowed in label value match
                        rule: '!self.contains(''*'')'
                    description: One or more labels that indicate a specific set of
                      pods/VMs on which a policy should be applied.
                    maxProperties: 4096
                    type: object
                    x-kubernetes-validations:
                    - message: wildcard not allowed in label key match
                      rule: self.all(key, !key.contains('*'))
                    - message: key must not be empty
                      rule: self.all(key, key.size() != 0)
                type: object
              sha256:
                description: SHA256 checksum that will be used to verify Wasm module
                  or OCI container.
                pattern: (^$|^[a-f0-9]{64}$)
                type: string
              targetRef:
                properties:
                  group:
                    description: group is the group of the target resource.
                    maxLength: 253
                    pattern: ^$|^[a-z0-9]([-a-z0-9]*[a-z0-9])?(\.[a-z0-9]([-a-z0-9]*[a-z0-9])?)*$
                    type: string
                  kind:
                    description: kind is kind of the target resource.
                    maxLength: 63
                    minLength: 1
                    pattern: ^[a-zA-Z]([-a-zA-Z0-9]*[a-zA-Z0-9])?$
                    type: string
                  name:
                    description: name is the name of the target resource.
                    maxLength: 253
                    minLength: 1
                    type: string
                  namespace:
                    description: namespace is the namespace of the referent.
                    type: string
                    x-kubernetes-validations:
                    - message: cross namespace referencing is not currently supported
                      rule: self.size() == 0
                required:
                - kind
                - name
                type: object
                x-kubernetes-validations:
                - message: Support kinds are core/Service and gateway.networking.k8s.io/Gateway
                  rule: '[self.group, self.kind] in [[''core'',''Service''], ['''',''Service''],
                    [''gateway.networking.k8s.io'',''Gateway'']]'
              targetRefs:
                description: Optional.
                items:
                  properties:
                    group:
                      description: group is the group of the target resource.
                      maxLength: 253
                      pattern: ^$|^[a-z0-9]([-a-z0-9]*[a-z0-9])?(\.[a-z0-9]([-a-z0-9]*[a-z0-9])?)*$
                      type: string
                    kind:
                      description: kind is kind of the target resource.
                      maxLength: 63
                      minLength: 1
                      pattern: ^[a-zA-Z]([-a-zA-Z0-9]*[a-zA-Z0-9])?$
                      type: string
                    name:
                      description: name is the name of the target resource.
                      maxLength: 253
                      minLength: 1
                      type: string
                    namespace:
                      description: namespace is the namespace of the referent.
                      type: string
                      x-kubernetes-validations:
                      - message: cross namespace referencing is not currently supported
                        rule: self.size() == 0
                  required:
                  - kind
                  - name
                  type: object
                  x-kubernetes-validations:
                  - message: Support kinds are core/Service and gateway.networking.k8s.io/Gateway
                    rule: '[self.group, self.kind] in [[''core'',''Service''], ['''',''Service''],
                      [''gateway.networking.k8s.io'',''Gateway'']]'
                type: array
              type:
                description: |-
                  Specifies the type of Wasm Extension to be used.

                  Valid Options: HTTP, NETWORK
                enum:
                - UNSPECIFIED_PLUGIN_TYPE
                - HTTP
                - NETWORK
                type: string
              url:
                description: URL of a Wasm module or OCI container.
                minLength: 1
                type: string
                x-kubernetes-validations:
                - message: url must have schema one of [http, https, file, oci]
                  rule: 'isURL(self) ? (url(self).getScheme() in ['''', ''http'',
                    ''https'', ''oci'', ''file'']) : (isURL(''http://'' + self) &&
                    url(''http://'' +self).getScheme() in ['''', ''http'', ''https'',
                    ''oci'', ''file''])'
              verificationKey:
                type: string
              vmConfig:
                description: Configuration for a Wasm VM.
                properties:
                  env:
                    description: Specifies environment variables to be injected to
                      this VM.
                    items:
                      properties:
                        name:
                          description: Name of the environment variable.
                          maxLength: 256
                          minLength: 1
                          type: string
                        value:
                          description: Value for the environment variable.
                          maxLength: 2048
                          type: string
                        valueFrom:
                          description: |-
                            Source for the environment variable's value.

                            Valid Options: INLINE, HOST
                          enum:
                          - INLINE
                          - HOST
                          type: string
                      required:
                      - name
                      type: object
                      x-kubernetes-validations:
                      - message: value may only be set when valueFrom is INLINE
                        rule: '(has(self.valueFrom) ? self.valueFrom : '''') != ''HOST''
                          || !has(self.value)'
                    maxItems: 256
                    type: array
                    x-kubernetes-list-map-keys:
                    - name
                    x-kubernetes-list-type: map
                type: object
            required:
            - url
            type: object
          status:
            type: object
            x-kubernetes-preserve-unknown-fields: true
        required:
        - spec
        type: object
    served: true
    storage: true
    subresources:
      status: {}
---
# Source: istio-microk8s/charts/base/templates/crds.yaml
apiVersion: apiextensions.k8s.io/v1
kind: CustomResourceDefinition
metadata:
  annotations:
    "helm.sh/resource-policy": keep
  labels:
    app: istio-pilot
    chart: istio
    heritage: Tiller
    release: istio
  name: destinationrules.networking.istio.io
spec:
  group: networking.istio.io
  names:
    categories:
    - istio-io
    - networking-istio-io
    kind: DestinationRule
    listKind: DestinationRuleList
    plural: destinationrules
    shortNames:
    - dr
    singular: destinationrule
  scope: Namespaced
  versions:
  - additionalPrinterColumns:
    - description: The name of a service from the service registry
      jsonPath: .spec.host
      name: Host
      type: string
    - description: 'CreationTimestamp is a timestamp representing the server time
        when this object was created. It is not guaranteed to be set in happens-before
        order across separate operations. Clients may not set this value. It is represented
        in RFC3339 form and is in UTC. Populated by the system. Read-only. Null for
        lists. More info: https://git.k8s.io/community/contributors/devel/api-conventions.md#metadata'
      jsonPath: .metadata.creationTimestamp
      name: Age
      type: date
    name: v1
    schema:
      openAPIV3Schema:
        properties:
          spec:
            description: 'Configuration affecting load balancing, outlier detection,
              etc. See more details at: https://istio.io/docs/reference/config/networking/destination-rule.html'
            properties:
              exportTo:
                description: A list of namespaces to which this destination rule is
                  exported.
                items:
                  type: string
                type: array
              host:
                description: The name of a service from the service registry.
                type: string
              subsets:
                description: One or more named sets that represent individual versions
                  of a service.
                items:
                  properties:
                    labels:
                      additionalProperties:
                        type: string
                      description: Labels apply a filter over the endpoints of a service
                        in the service registry.
                      type: object
                    name:
                      description: Name of the subset.
                      type: string
                    trafficPolicy:
                      description: Traffic policies that apply to this subset.
                      properties:
                        connectionPool:
                          properties:
                            http:
                              description: HTTP connection pool settings.
                              properties:
                                h2UpgradePolicy:
                                  description: |-
                                    Specify if http1.1 connection should be upgraded to http2 for the associated destination.

                                    Valid Options: DEFAULT, DO_NOT_UPGRADE, UPGRADE
                                  enum:
                                  - DEFAULT
                                  - DO_NOT_UPGRADE
                                  - UPGRADE
                                  type: string
                                http1MaxPendingRequests:
                                  description: Maximum number of requests that will
                                    be queued while waiting for a ready connection
                                    pool connection.
                                  format: int32
                                  type: integer
                                http2MaxRequests:
                                  description: Maximum number of active requests to
                                    a destination.
                                  format: int32
                                  type: integer
                                idleTimeout:
                                  description: The idle timeout for upstream connection
                                    pool connections.
                                  type: string
                                  x-kubernetes-validations:
                                  - message: must be a valid duration greater than
                                      1ms
                                    rule: duration(self) >= duration('1ms')
                                maxConcurrentStreams:
                                  description: The maximum number of concurrent streams
                                    allowed for a peer on one HTTP/2 connection.
                                  format: int32
                                  type: integer
                                maxRequestsPerConnection:
                                  description: Maximum number of requests per connection
                                    to a backend.
                                  format: int32
                                  type: integer
                                maxRetries:
                                  description: Maximum number of retries that can
                                    be outstanding to all hosts in a cluster at a
                                    given time.
                                  format: int32
                                  type: integer
                                useClientProtocol:
                                  description: If set to true, client protocol will
                                    be preserved while initiating connection to backend.
                                  type: boolean
                              type: object
                            tcp:
                              description: Settings common to both HTTP and TCP upstream
                                connections.
                              properties:
                                connectTimeout:
                                  description: TCP connection timeout.
                                  type: string
                                  x-kubernetes-validations:
                                  - message: must be a valid duration greater than
                                      1ms
                                    rule: duration(self) >= duration('1ms')
                                idleTimeout:
                                  description: The idle timeout for TCP connections.
                                  type: string
                                  x-kubernetes-validations:
                                  - message: must be a valid duration greater than
                                      1ms
                                    rule: duration(self) >= duration('1ms')
                                maxConnectionDuration:
                                  description: The maximum duration of a connection.
                                  type: string
                                  x-kubernetes-validations:
                                  - message: must be a valid duration greater than
                                      1ms
                                    rule: duration(self) >= duration('1ms')
                                maxConnections:
                                  description: Maximum number of HTTP1 /TCP connections
                                    to a destination host.
                                  format: int32
                                  type: integer
                                tcpKeepalive:
                                  description: If set then set SO_KEEPALIVE on the
                                    socket to enable TCP Keepalives.
                                  properties:
                                    interval:
                                      description: The time duration between keep-alive
                                        probes.
                                      type: string
                                      x-kubernetes-validations:
                                      - message: must be a valid duration greater
                                          than 1ms
                                        rule: duration(self) >= duration('1ms')
                                    probes:
                                      description: Maximum number of keepalive probes
                                        to send without response before deciding the
                                        connection is dead.
                                      maximum: 4294967295
                                      minimum: 0
                                      type: integer
                                    time:
                                      description: The time duration a connection
                                        needs to be idle before keep-alive probes
                                        start being sent.
                                      type: string
                                      x-kubernetes-validations:
                                      - message: must be a valid duration greater
                                          than 1ms
                                        rule: duration(self) >= duration('1ms')
                                  type: object
                              type: object
                          type: object
                        loadBalancer:
                          description: Settings controlling the load balancer algorithms.
                          oneOf:
                          - not:
                              anyOf:
                              - required:
                                - simple
                              - required:
                                - consistentHash
                          - required:
                            - simple
                          - required:
                            - consistentHash
                          properties:
                            consistentHash:
                              allOf:
                              - oneOf:
                                - not:
                                    anyOf:
                                    - required:
                                      - httpHeaderName
                                    - required:
                                      - httpCookie
                                    - required:
                                      - useSourceIp
                                    - required:
                                      - httpQueryParameterName
                                - required:
                                  - httpHeaderName
                                - required:
                                  - httpCookie
                                - required:
                                  - useSourceIp
                                - required:
                                  - httpQueryParameterName
                              - oneOf:
                                - not:
                                    anyOf:
                                    - required:
                                      - ringHash
                                    - required:
                                      - maglev
                                - required:
                                  - ringHash
                                - required:
                                  - maglev
                              properties:
                                httpCookie:
                                  description: Hash based on HTTP cookie.
                                  properties:
                                    name:
                                      description: Name of the cookie.
                                      type: string
                                    path:
                                      description: Path to set for the cookie.
                                      type: string
                                    ttl:
                                      description: Lifetime of the cookie.
                                      type: string
                                  required:
                                  - name
                                  type: object
                                httpHeaderName:
                                  description: Hash based on a specific HTTP header.
                                  type: string
                                httpQueryParameterName:
                                  description: Hash based on a specific HTTP query
                                    parameter.
                                  type: string
                                maglev:
                                  description: The Maglev load balancer implements
                                    consistent hashing to backend hosts.
                                  properties:
                                    tableSize:
                                      description: The table size for Maglev hashing.
                                      minimum: 0
                                      type: integer
                                  type: object
                                minimumRingSize:
                                  description: Deprecated.
                                  minimum: 0
                                  type: integer
                                ringHash:
                                  description: The ring/modulo hash load balancer
                                    implements consistent hashing to backend hosts.
                                  properties:
                                    minimumRingSize:
                                      description: The minimum number of virtual nodes
                                        to use for the hash ring.
                                      minimum: 0
                                      type: integer
                                  type: object
                                useSourceIp:
                                  description: Hash based on the source IP address.
                                  type: boolean
                              type: object
                            localityLbSetting:
                              properties:
                                distribute:
                                  description: 'Optional: only one of distribute,
                                    failover or failoverPriority can be set.'
                                  items:
                                    properties:
                                      from:
                                        description: Originating locality, '/' separated,
                                          e.g.
                                        type: string
                                      to:
                                        additionalProperties:
                                          maximum: 4294967295
                                          minimum: 0
                                          type: integer
                                        description: Map of upstream localities to
                                          traffic distribution weights.
                                        type: object
                                    type: object
                                  type: array
                                enabled:
                                  description: enable locality load balancing, this
                                    is DestinationRule-level and will override mesh
                                    wide settings in entirety.
                                  nullable: true
                                  type: boolean
                                failover:
                                  description: 'Optional: only one of distribute,
                                    failover or failoverPriority can be set.'
                                  items:
                                    properties:
                                      from:
                                        description: Originating region.
                                        type: string
                                      to:
                                        description: Destination region the traffic
                                          will fail over to when endpoints in the
                                          'from' region becomes unhealthy.
                                        type: string
                                    type: object
                                  type: array
                                failoverPriority:
                                  description: failoverPriority is an ordered list
                                    of labels used to sort endpoints to do priority
                                    based load balancing.
                                  items:
                                    type: string
                                  type: array
                              type: object
                            simple:
                              description: |2-


                                Valid Options: LEAST_CONN, RANDOM, PASSTHROUGH, ROUND_ROBIN, LEAST_REQUEST
                              enum:
                              - UNSPECIFIED
                              - LEAST_CONN
                              - RANDOM
                              - PASSTHROUGH
                              - ROUND_ROBIN
                              - LEAST_REQUEST
                              type: string
                            warmupDurationSecs:
                              description: Represents the warmup duration of Service.
                              type: string
                              x-kubernetes-validations:
                              - message: must be a valid duration greater than 1ms
                                rule: duration(self) >= duration('1ms')
                          type: object
                        outlierDetection:
                          properties:
                            baseEjectionTime:
                              description: Minimum ejection duration.
                              type: string
                              x-kubernetes-validations:
                              - message: must be a valid duration greater than 1ms
                                rule: duration(self) >= duration('1ms')
                            consecutive5xxErrors:
                              description: Number of 5xx errors before a host is ejected
                                from the connection pool.
                              maximum: 4294967295
                              minimum: 0
                              nullable: true
                              type: integer
                            consecutiveErrors:
                              format: int32
                              type: integer
                            consecutiveGatewayErrors:
                              description: Number of gateway errors before a host
                                is ejected from the connection pool.
                              maximum: 4294967295
                              minimum: 0
                              nullable: true
                              type: integer
                            consecutiveLocalOriginFailures:
                              description: The number of consecutive locally originated
                                failures before ejection occurs.
                              maximum: 4294967295
                              minimum: 0
                              nullable: true
                              type: integer
                            interval:
                              description: Time interval between ejection sweep analysis.
                              type: string
                              x-kubernetes-validations:
                              - message: must be a valid duration greater than 1ms
                                rule: duration(self) >= duration('1ms')
                            maxEjectionPercent:
                              description: Maximum % of hosts in the load balancing
                                pool for the upstream service that can be ejected.
                              format: int32
                              type: integer
                            minHealthPercent:
                              description: Outlier detection will be enabled as long
                                as the associated load balancing pool has at least
                                min_health_percent hosts in healthy mode.
                              format: int32
                              type: integer
                            splitExternalLocalOriginErrors:
                              description: Determines whether to distinguish local
                                origin failures from external errors.
                              type: boolean
                          type: object
                        portLevelSettings:
                          description: Traffic policies specific to individual ports.
                          items:
                            properties:
                              connectionPool:
                                properties:
                                  http:
                                    description: HTTP connection pool settings.
                                    properties:
                                      h2UpgradePolicy:
                                        description: |-
                                          Specify if http1.1 connection should be upgraded to http2 for the associated destination.

                                          Valid Options: DEFAULT, DO_NOT_UPGRADE, UPGRADE
                                        enum:
                                        - DEFAULT
                                        - DO_NOT_UPGRADE
                                        - UPGRADE
                                        type: string
                                      http1MaxPendingRequests:
                                        description: Maximum number of requests that
                                          will be queued while waiting for a ready
                                          connection pool connection.
                                        format: int32
                                        type: integer
                                      http2MaxRequests:
                                        description: Maximum number of active requests
                                          to a destination.
                                        format: int32
                                        type: integer
                                      idleTimeout:
                                        description: The idle timeout for upstream
                                          connection pool connections.
                                        type: string
                                        x-kubernetes-validations:
                                        - message: must be a valid duration greater
                                            than 1ms
                                          rule: duration(self) >= duration('1ms')
                                      maxConcurrentStreams:
                                        description: The maximum number of concurrent
                                          streams allowed for a peer on one HTTP/2
                                          connection.
                                        format: int32
                                        type: integer
                                      maxRequestsPerConnection:
                                        description: Maximum number of requests per
                                          connection to a backend.
                                        format: int32
                                        type: integer
                                      maxRetries:
                                        description: Maximum number of retries that
                                          can be outstanding to all hosts in a cluster
                                          at a given time.
                                        format: int32
                                        type: integer
                                      useClientProtocol:
                                        description: If set to true, client protocol
                                          will be preserved while initiating connection
                                          to backend.
                                        type: boolean
                                    type: object
                                  tcp:
                                    description: Settings common to both HTTP and
                                      TCP upstream connections.
                                    properties:
                                      connectTimeout:
                                        description: TCP connection timeout.
                                        type: string
                                        x-kubernetes-validations:
                                        - message: must be a valid duration greater
                                            than 1ms
                                          rule: duration(self) >= duration('1ms')
                                      idleTimeout:
                                        description: The idle timeout for TCP connections.
                                        type: string
                                        x-kubernetes-validations:
                                        - message: must be a valid duration greater
                                            than 1ms
                                          rule: duration(self) >= duration('1ms')
                                      maxConnectionDuration:
                                        description: The maximum duration of a connection.
                                        type: string
                                        x-kubernetes-validations:
                                        - message: must be a valid duration greater
                                            than 1ms
                                          rule: duration(self) >= duration('1ms')
                                      maxConnections:
                                        description: Maximum number of HTTP1 /TCP
                                          connections to a destination host.
                                        format: int32
                                        type: integer
                                      tcpKeepalive:
                                        description: If set then set SO_KEEPALIVE
                                          on the socket to enable TCP Keepalives.
                                        properties:
                                          interval:
                                            description: The time duration between
                                              keep-alive probes.
                                            type: string
                                            x-kubernetes-validations:
                                            - message: must be a valid duration greater
                                                than 1ms
                                              rule: duration(self) >= duration('1ms')
                                          probes:
                                            description: Maximum number of keepalive
                                              probes to send without response before
                                              deciding the connection is dead.
                                            maximum: 4294967295
                                            minimum: 0
                                            type: integer
                                          time:
                                            description: The time duration a connection
                                              needs to be idle before keep-alive probes
                                              start being sent.
                                            type: string
                                            x-kubernetes-validations:
                                            - message: must be a valid duration greater
                                                than 1ms
                                              rule: duration(self) >= duration('1ms')
                                        type: object
                                    type: object
                                type: object
                              loadBalancer:
                                description: Settings controlling the load balancer
                                  algorithms.
                                oneOf:
                                - not:
                                    anyOf:
                                    - required:
                                      - simple
                                    - required:
                                      - consistentHash
                                - required:
                                  - simple
                                - required:
                                  - consistentHash
                                properties:
                                  consistentHash:
                                    allOf:
                                    - oneOf:
                                      - not:
                                          anyOf:
                                          - required:
                                            - httpHeaderName
                                          - required:
                                            - httpCookie
                                          - required:
                                            - useSourceIp
                                          - required:
                                            - httpQueryParameterName
                                      - required:
                                        - httpHeaderName
                                      - required:
                                        - httpCookie
                                      - required:
                                        - useSourceIp
                                      - required:
                                        - httpQueryParameterName
                                    - oneOf:
                                      - not:
                                          anyOf:
                                          - required:
                                            - ringHash
                                          - required:
                                            - maglev
                                      - required:
                                        - ringHash
                                      - required:
                                        - maglev
                                    properties:
                                      httpCookie:
                                        description: Hash based on HTTP cookie.
                                        properties:
                                          name:
                                            description: Name of the cookie.
                                            type: string
                                          path:
                                            description: Path to set for the cookie.
                                            type: string
                                          ttl:
                                            description: Lifetime of the cookie.
                                            type: string
                                        required:
                                        - name
                                        type: object
                                      httpHeaderName:
                                        description: Hash based on a specific HTTP
                                          header.
                                        type: string
                                      httpQueryParameterName:
                                        description: Hash based on a specific HTTP
                                          query parameter.
                                        type: string
                                      maglev:
                                        description: The Maglev load balancer implements
                                          consistent hashing to backend hosts.
                                        properties:
                                          tableSize:
                                            description: The table size for Maglev
                                              hashing.
                                            minimum: 0
                                            type: integer
                                        type: object
                                      minimumRingSize:
                                        description: Deprecated.
                                        minimum: 0
                                        type: integer
                                      ringHash:
                                        description: The ring/modulo hash load balancer
                                          implements consistent hashing to backend
                                          hosts.
                                        properties:
                                          minimumRingSize:
                                            description: The minimum number of virtual
                                              nodes to use for the hash ring.
                                            minimum: 0
                                            type: integer
                                        type: object
                                      useSourceIp:
                                        description: Hash based on the source IP address.
                                        type: boolean
                                    type: object
                                  localityLbSetting:
                                    properties:
                                      distribute:
                                        description: 'Optional: only one of distribute,
                                          failover or failoverPriority can be set.'
                                        items:
                                          properties:
                                            from:
                                              description: Originating locality, '/'
                                                separated, e.g.
                                              type: string
                                            to:
                                              additionalProperties:
                                                maximum: 4294967295
                                                minimum: 0
                                                type: integer
                                              description: Map of upstream localities
                                                to traffic distribution weights.
                                              type: object
                                          type: object
                                        type: array
                                      enabled:
                                        description: enable locality load balancing,
                                          this is DestinationRule-level and will override
                                          mesh wide settings in entirety.
                                        nullable: true
                                        type: boolean
                                      failover:
                                        description: 'Optional: only one of distribute,
                                          failover or failoverPriority can be set.'
                                        items:
                                          properties:
                                            from:
                                              description: Originating region.
                                              type: string
                                            to:
                                              description: Destination region the
                                                traffic will fail over to when endpoints
                                                in the 'from' region becomes unhealthy.
                                              type: string
                                          type: object
                                        type: array
                                      failoverPriority:
                                        description: failoverPriority is an ordered
                                          list of labels used to sort endpoints to
                                          do priority based load balancing.
                                        items:
                                          type: string
                                        type: array
                                    type: object
                                  simple:
                                    description: |2-


                                      Valid Options: LEAST_CONN, RANDOM, PASSTHROUGH, ROUND_ROBIN, LEAST_REQUEST
                                    enum:
                                    - UNSPECIFIED
                                    - LEAST_CONN
                                    - RANDOM
                                    - PASSTHROUGH
                                    - ROUND_ROBIN
                                    - LEAST_REQUEST
                                    type: string
                                  warmupDurationSecs:
                                    description: Represents the warmup duration of
                                      Service.
                                    type: string
                                    x-kubernetes-validations:
                                    - message: must be a valid duration greater than
                                        1ms
                                      rule: duration(self) >= duration('1ms')
                                type: object
                              outlierDetection:
                                properties:
                                  baseEjectionTime:
                                    description: Minimum ejection duration.
                                    type: string
                                    x-kubernetes-validations:
                                    - message: must be a valid duration greater than
                                        1ms
                                      rule: duration(self) >= duration('1ms')
                                  consecutive5xxErrors:
                                    description: Number of 5xx errors before a host
                                      is ejected from the connection pool.
                                    maximum: 4294967295
                                    minimum: 0
                                    nullable: true
                                    type: integer
                                  consecutiveErrors:
                                    format: int32
                                    type: integer
                                  consecutiveGatewayErrors:
                                    description: Number of gateway errors before a
                                      host is ejected from the connection pool.
                                    maximum: 4294967295
                                    minimum: 0
                                    nullable: true
                                    type: integer
                                  consecutiveLocalOriginFailures:
                                    description: The number of consecutive locally
                                      originated failures before ejection occurs.
                                    maximum: 4294967295
                                    minimum: 0
                                    nullable: true
                                    type: integer
                                  interval:
                                    description: Time interval between ejection sweep
                                      analysis.
                                    type: string
                                    x-kubernetes-validations:
                                    - message: must be a valid duration greater than
                                        1ms
                                      rule: duration(self) >= duration('1ms')
                                  maxEjectionPercent:
                                    description: Maximum % of hosts in the load balancing
                                      pool for the upstream service that can be ejected.
                                    format: int32
                                    type: integer
                                  minHealthPercent:
                                    description: Outlier detection will be enabled
                                      as long as the associated load balancing pool
                                      has at least min_health_percent hosts in healthy
                                      mode.
                                    format: int32
                                    type: integer
                                  splitExternalLocalOriginErrors:
                                    description: Determines whether to distinguish
                                      local origin failures from external errors.
                                    type: boolean
                                type: object
                              port:
                                description: Specifies the number of a port on the
                                  destination service on which this policy is being
                                  applied.
                                properties:
                                  number:
                                    maximum: 4294967295
                                    minimum: 0
                                    type: integer
                                type: object
                              tls:
                                description: TLS related settings for connections
                                  to the upstream service.
                                properties:
                                  caCertificates:
                                    description: 'OPTIONAL: The path to the file containing
                                      certificate authority certificates to use in
                                      verifying a presented server certificate.'
                                    type: string
                                  caCrl:
                                    description: 'OPTIONAL: The path to the file containing
                                      the certificate revocation list (CRL) to use
                                      in verifying a presented server certificate.'
                                    type: string
                                  clientCertificate:
                                    description: REQUIRED if mode is `MUTUAL`.
                                    type: string
                                  credentialName:
                                    description: The name of the secret that holds
                                      the TLS certs for the client including the CA
                                      certificates.
                                    type: string
                                  insecureSkipVerify:
                                    description: '`insecureSkipVerify` specifies whether
                                      the proxy should skip verifying the CA signature
                                      and SAN for the server certificate corresponding
                                      to the host.'
                                    nullable: true
                                    type: boolean
                                  mode:
                                    description: |-
                                      Indicates whether connections to this port should be secured using TLS.

                                      Valid Options: DISABLE, SIMPLE, MUTUAL, ISTIO_MUTUAL
                                    enum:
                                    - DISABLE
                                    - SIMPLE
                                    - MUTUAL
                                    - ISTIO_MUTUAL
                                    type: string
                                  privateKey:
                                    description: REQUIRED if mode is `MUTUAL`.
                                    type: string
                                  sni:
                                    description: SNI string to present to the server
                                      during TLS handshake.
                                    type: string
                                  subjectAltNames:
                                    description: A list of alternate names to verify
                                      the subject identity in the certificate.
                                    items:
                                      type: string
                                    type: array
                                type: object
                            type: object
                          maxItems: 4096
                          type: array
                        proxyProtocol:
                          description: The upstream PROXY protocol settings.
                          properties:
                            version:
                              description: |-
                                The PROXY protocol version to use.

                                Valid Options: V1, V2
                              enum:
                              - V1
                              - V2
                              type: string
                          type: object
                        tls:
                          description: TLS related settings for connections to the
                            upstream service.
                          properties:
                            caCertificates:
                              description: 'OPTIONAL: The path to the file containing
                                certificate authority certificates to use in verifying
                                a presented server certificate.'
                              type: string
                            caCrl:
                              description: 'OPTIONAL: The path to the file containing
                                the certificate revocation list (CRL) to use in verifying
                                a presented server certificate.'
                              type: string
                            clientCertificate:
                              description: REQUIRED if mode is `MUTUAL`.
                              type: string
                            credentialName:
                              description: The name of the secret that holds the TLS
                                certs for the client including the CA certificates.
                              type: string
                            insecureSkipVerify:
                              description: '`insecureSkipVerify` specifies whether
                                the proxy should skip verifying the CA signature and
                                SAN for the server certificate corresponding to the
                                host.'
                              nullable: true
                              type: boolean
                            mode:
                              description: |-
                                Indicates whether connections to this port should be secured using TLS.

                                Valid Options: DISABLE, SIMPLE, MUTUAL, ISTIO_MUTUAL
                              enum:
                              - DISABLE
                              - SIMPLE
                              - MUTUAL
                              - ISTIO_MUTUAL
                              type: string
                            privateKey:
                              description: REQUIRED if mode is `MUTUAL`.
                              type: string
                            sni:
                              description: SNI string to present to the server during
                                TLS handshake.
                              type: string
                            subjectAltNames:
                              description: A list of alternate names to verify the
                                subject identity in the certificate.
                              items:
                                type: string
                              type: array
                          type: object
                        tunnel:
                          description: Configuration of tunneling TCP over other transport
                            or application layers for the host configured in the DestinationRule.
                          properties:
                            protocol:
                              description: Specifies which protocol to use for tunneling
                                the downstream connection.
                              type: string
                            targetHost:
                              description: Specifies a host to which the downstream
                                connection is tunneled.
                              type: string
                            targetPort:
                              description: Specifies a port to which the downstream
                                connection is tunneled.
                              maximum: 4294967295
                              minimum: 0
                              type: integer
                          required:
                          - targetHost
                          - targetPort
                          type: object
                      type: object
                  required:
                  - name
                  type: object
                type: array
              trafficPolicy:
                description: Traffic policies to apply (load balancing policy, connection
                  pool sizes, outlier detection).
                properties:
                  connectionPool:
                    properties:
                      http:
                        description: HTTP connection pool settings.
                        properties:
                          h2UpgradePolicy:
                            description: |-
                              Specify if http1.1 connection should be upgraded to http2 for the associated destination.

                              Valid Options: DEFAULT, DO_NOT_UPGRADE, UPGRADE
                            enum:
                            - DEFAULT
                            - DO_NOT_UPGRADE
                            - UPGRADE
                            type: string
                          http1MaxPendingRequests:
                            description: Maximum number of requests that will be queued
                              while waiting for a ready connection pool connection.
                            format: int32
                            type: integer
                          http2MaxRequests:
                            description: Maximum number of active requests to a destination.
                            format: int32
                            type: integer
                          idleTimeout:
                            description: The idle timeout for upstream connection
                              pool connections.
                            type: string
                            x-kubernetes-validations:
                            - message: must be a valid duration greater than 1ms
                              rule: duration(self) >= duration('1ms')
                          maxConcurrentStreams:
                            description: The maximum number of concurrent streams
                              allowed for a peer on one HTTP/2 connection.
                            format: int32
                            type: integer
                          maxRequestsPerConnection:
                            description: Maximum number of requests per connection
                              to a backend.
                            format: int32
                            type: integer
                          maxRetries:
                            description: Maximum number of retries that can be outstanding
                              to all hosts in a cluster at a given time.
                            format: int32
                            type: integer
                          useClientProtocol:
                            description: If set to true, client protocol will be preserved
                              while initiating connection to backend.
                            type: boolean
                        type: object
                      tcp:
                        description: Settings common to both HTTP and TCP upstream
                          connections.
                        properties:
                          connectTimeout:
                            description: TCP connection timeout.
                            type: string
                            x-kubernetes-validations:
                            - message: must be a valid duration greater than 1ms
                              rule: duration(self) >= duration('1ms')
                          idleTimeout:
                            description: The idle timeout for TCP connections.
                            type: string
                            x-kubernetes-validations:
                            - message: must be a valid duration greater than 1ms
                              rule: duration(self) >= duration('1ms')
                          maxConnectionDuration:
                            description: The maximum duration of a connection.
                            type: string
                            x-kubernetes-validations:
                            - message: must be a valid duration greater than 1ms
                              rule: duration(self) >= duration('1ms')
                          maxConnections:
                            description: Maximum number of HTTP1 /TCP connections
                              to a destination host.
                            format: int32
                            type: integer
                          tcpKeepalive:
                            description: If set then set SO_KEEPALIVE on the socket
                              to enable TCP Keepalives.
                            properties:
                              interval:
                                description: The time duration between keep-alive
                                  probes.
                                type: string
                                x-kubernetes-validations:
                                - message: must be a valid duration greater than 1ms
                                  rule: duration(self) >= duration('1ms')
                              probes:
                                description: Maximum number of keepalive probes to
                                  send without response before deciding the connection
                                  is dead.
                                maximum: 4294967295
                                minimum: 0
                                type: integer
                              time:
                                description: The time duration a connection needs
                                  to be idle before keep-alive probes start being
                                  sent.
                                type: string
                                x-kubernetes-validations:
                                - message: must be a valid duration greater than 1ms
                                  rule: duration(self) >= duration('1ms')
                            type: object
                        type: object
                    type: object
                  loadBalancer:
                    description: Settings controlling the load balancer algorithms.
                    oneOf:
                    - not:
                        anyOf:
                        - required:
                          - simple
                        - required:
                          - consistentHash
                    - required:
                      - simple
                    - required:
                      - consistentHash
                    properties:
                      consistentHash:
                        allOf:
                        - oneOf:
                          - not:
                              anyOf:
                              - required:
                                - httpHeaderName
                              - required:
                                - httpCookie
                              - required:
                                - useSourceIp
                              - required:
                                - httpQueryParameterName
                          - required:
                            - httpHeaderName
                          - required:
                            - httpCookie
                          - required:
                            - useSourceIp
                          - required:
                            - httpQueryParameterName
                        - oneOf:
                          - not:
                              anyOf:
                              - required:
                                - ringHash
                              - required:
                                - maglev
                          - required:
                            - ringHash
                          - required:
                            - maglev
                        properties:
                          httpCookie:
                            description: Hash based on HTTP cookie.
                            properties:
                              name:
                                description: Name of the cookie.
                                type: string
                              path:
                                description: Path to set for the cookie.
                                type: string
                              ttl:
                                description: Lifetime of the cookie.
                                type: string
                            required:
                            - name
                            type: object
                          httpHeaderName:
                            description: Hash based on a specific HTTP header.
                            type: string
                          httpQueryParameterName:
                            description: Hash based on a specific HTTP query parameter.
                            type: string
                          maglev:
                            description: The Maglev load balancer implements consistent
                              hashing to backend hosts.
                            properties:
                              tableSize:
                                description: The table size for Maglev hashing.
                                minimum: 0
                                type: integer
                            type: object
                          minimumRingSize:
                            description: Deprecated.
                            minimum: 0
                            type: integer
                          ringHash:
                            description: The ring/modulo hash load balancer implements
                              consistent hashing to backend hosts.
                            properties:
                              minimumRingSize:
                                description: The minimum number of virtual nodes to
                                  use for the hash ring.
                                minimum: 0
                                type: integer
                            type: object
                          useSourceIp:
                            description: Hash based on the source IP address.
                            type: boolean
                        type: object
                      localityLbSetting:
                        properties:
                          distribute:
                            description: 'Optional: only one of distribute, failover
                              or failoverPriority can be set.'
                            items:
                              properties:
                                from:
                                  description: Originating locality, '/' separated,
                                    e.g.
                                  type: string
                                to:
                                  additionalProperties:
                                    maximum: 4294967295
                                    minimum: 0
                                    type: integer
                                  description: Map of upstream localities to traffic
                                    distribution weights.
                                  type: object
                              type: object
                            type: array
                          enabled:
                            description: enable locality load balancing, this is DestinationRule-level
                              and will override mesh wide settings in entirety.
                            nullable: true
                            type: boolean
                          failover:
                            description: 'Optional: only one of distribute, failover
                              or failoverPriority can be set.'
                            items:
                              properties:
                                from:
                                  description: Originating region.
                                  type: string
                                to:
                                  description: Destination region the traffic will
                                    fail over to when endpoints in the 'from' region
                                    becomes unhealthy.
                                  type: string
                              type: object
                            type: array
                          failoverPriority:
                            description: failoverPriority is an ordered list of labels
                              used to sort endpoints to do priority based load balancing.
                            items:
                              type: string
                            type: array
                        type: object
                      simple:
                        description: |2-


                          Valid Options: LEAST_CONN, RANDOM, PASSTHROUGH, ROUND_ROBIN, LEAST_REQUEST
                        enum:
                        - UNSPECIFIED
                        - LEAST_CONN
                        - RANDOM
                        - PASSTHROUGH
                        - ROUND_ROBIN
                        - LEAST_REQUEST
                        type: string
                      warmupDurationSecs:
                        description: Represents the warmup duration of Service.
                        type: string
                        x-kubernetes-validations:
                        - message: must be a valid duration greater than 1ms
                          rule: duration(self) >= duration('1ms')
                    type: object
                  outlierDetection:
                    properties:
                      baseEjectionTime:
                        description: Minimum ejection duration.
                        type: string
                        x-kubernetes-validations:
                        - message: must be a valid duration greater than 1ms
                          rule: duration(self) >= duration('1ms')
                      consecutive5xxErrors:
                        description: Number of 5xx errors before a host is ejected
                          from the connection pool.
                        maximum: 4294967295
                        minimum: 0
                        nullable: true
                        type: integer
                      consecutiveErrors:
                        format: int32
                        type: integer
                      consecutiveGatewayErrors:
                        description: Number of gateway errors before a host is ejected
                          from the connection pool.
                        maximum: 4294967295
                        minimum: 0
                        nullable: true
                        type: integer
                      consecutiveLocalOriginFailures:
                        description: The number of consecutive locally originated
                          failures before ejection occurs.
                        maximum: 4294967295
                        minimum: 0
                        nullable: true
                        type: integer
                      interval:
                        description: Time interval between ejection sweep analysis.
                        type: string
                        x-kubernetes-validations:
                        - message: must be a valid duration greater than 1ms
                          rule: duration(self) >= duration('1ms')
                      maxEjectionPercent:
                        description: Maximum % of hosts in the load balancing pool
                          for the upstream service that can be ejected.
                        format: int32
                        type: integer
                      minHealthPercent:
                        description: Outlier detection will be enabled as long as
                          the associated load balancing pool has at least min_health_percent
                          hosts in healthy mode.
                        format: int32
                        type: integer
                      splitExternalLocalOriginErrors:
                        description: Determines whether to distinguish local origin
                          failures from external errors.
                        type: boolean
                    type: object
                  portLevelSettings:
                    description: Traffic policies specific to individual ports.
                    items:
                      properties:
                        connectionPool:
                          properties:
                            http:
                              description: HTTP connection pool settings.
                              properties:
                                h2UpgradePolicy:
                                  description: |-
                                    Specify if http1.1 connection should be upgraded to http2 for the associated destination.

                                    Valid Options: DEFAULT, DO_NOT_UPGRADE, UPGRADE
                                  enum:
                                  - DEFAULT
                                  - DO_NOT_UPGRADE
                                  - UPGRADE
                                  type: string
                                http1MaxPendingRequests:
                                  description: Maximum number of requests that will
                                    be queued while waiting for a ready connection
                                    pool connection.
                                  format: int32
                                  type: integer
                                http2MaxRequests:
                                  description: Maximum number of active requests to
                                    a destination.
                                  format: int32
                                  type: integer
                                idleTimeout:
                                  description: The idle timeout for upstream connection
                                    pool connections.
                                  type: string
                                  x-kubernetes-validations:
                                  - message: must be a valid duration greater than
                                      1ms
                                    rule: duration(self) >= duration('1ms')
                                maxConcurrentStreams:
                                  description: The maximum number of concurrent streams
                                    allowed for a peer on one HTTP/2 connection.
                                  format: int32
                                  type: integer
                                maxRequestsPerConnection:
                                  description: Maximum number of requests per connection
                                    to a backend.
                                  format: int32
                                  type: integer
                                maxRetries:
                                  description: Maximum number of retries that can
                                    be outstanding to all hosts in a cluster at a
                                    given time.
                                  format: int32
                                  type: integer
                                useClientProtocol:
                                  description: If set to true, client protocol will
                                    be preserved while initiating connection to backend.
                                  type: boolean
                              type: object
                            tcp:
                              description: Settings common to both HTTP and TCP upstream
                                connections.
                              properties:
                                connectTimeout:
                                  description: TCP connection timeout.
                                  type: string
                                  x-kubernetes-validations:
                                  - message: must be a valid duration greater than
                                      1ms
                                    rule: duration(self) >= duration('1ms')
                                idleTimeout:
                                  description: The idle timeout for TCP connections.
                                  type: string
                                  x-kubernetes-validations:
                                  - message: must be a valid duration greater than
                                      1ms
                                    rule: duration(self) >= duration('1ms')
                                maxConnectionDuration:
                                  description: The maximum duration of a connection.
                                  type: string
                                  x-kubernetes-validations:
                                  - message: must be a valid duration greater than
                                      1ms
                                    rule: duration(self) >= duration('1ms')
                                maxConnections:
                                  description: Maximum number of HTTP1 /TCP connections
                                    to a destination host.
                                  format: int32
                                  type: integer
                                tcpKeepalive:
                                  description: If set then set SO_KEEPALIVE on the
                                    socket to enable TCP Keepalives.
                                  properties:
                                    interval:
                                      description: The time duration between keep-alive
                                        probes.
                                      type: string
                                      x-kubernetes-validations:
                                      - message: must be a valid duration greater
                                          than 1ms
                                        rule: duration(self) >= duration('1ms')
                                    probes:
                                      description: Maximum number of keepalive probes
                                        to send without response before deciding the
                                        connection is dead.
                                      maximum: 4294967295
                                      minimum: 0
                                      type: integer
                                    time:
                                      description: The time duration a connection
                                        needs to be idle before keep-alive probes
                                        start being sent.
                                      type: string
                                      x-kubernetes-validations:
                                      - message: must be a valid duration greater
                                          than 1ms
                                        rule: duration(self) >= duration('1ms')
                                  type: object
                              type: object
                          type: object
                        loadBalancer:
                          description: Settings controlling the load balancer algorithms.
                          oneOf:
                          - not:
                              anyOf:
                              - required:
                                - simple
                              - required:
                                - consistentHash
                          - required:
                            - simple
                          - required:
                            - consistentHash
                          properties:
                            consistentHash:
                              allOf:
                              - oneOf:
                                - not:
                                    anyOf:
                                    - required:
                                      - httpHeaderName
                                    - required:
                                      - httpCookie
                                    - required:
                                      - useSourceIp
                                    - required:
                                      - httpQueryParameterName
                                - required:
                                  - httpHeaderName
                                - required:
                                  - httpCookie
                                - required:
                                  - useSourceIp
                                - required:
                                  - httpQueryParameterName
                              - oneOf:
                                - not:
                                    anyOf:
                                    - required:
                                      - ringHash
                                    - required:
                                      - maglev
                                - required:
                                  - ringHash
                                - required:
                                  - maglev
                              properties:
                                httpCookie:
                                  description: Hash based on HTTP cookie.
                                  properties:
                                    name:
                                      description: Name of the cookie.
                                      type: string
                                    path:
                                      description: Path to set for the cookie.
                                      type: string
                                    ttl:
                                      description: Lifetime of the cookie.
                                      type: string
                                  required:
                                  - name
                                  type: object
                                httpHeaderName:
                                  description: Hash based on a specific HTTP header.
                                  type: string
                                httpQueryParameterName:
                                  description: Hash based on a specific HTTP query
                                    parameter.
                                  type: string
                                maglev:
                                  description: The Maglev load balancer implements
                                    consistent hashing to backend hosts.
                                  properties:
                                    tableSize:
                                      description: The table size for Maglev hashing.
                                      minimum: 0
                                      type: integer
                                  type: object
                                minimumRingSize:
                                  description: Deprecated.
                                  minimum: 0
                                  type: integer
                                ringHash:
                                  description: The ring/modulo hash load balancer
                                    implements consistent hashing to backend hosts.
                                  properties:
                                    minimumRingSize:
                                      description: The minimum number of virtual nodes
                                        to use for the hash ring.
                                      minimum: 0
                                      type: integer
                                  type: object
                                useSourceIp:
                                  description: Hash based on the source IP address.
                                  type: boolean
                              type: object
                            localityLbSetting:
                              properties:
                                distribute:
                                  description: 'Optional: only one of distribute,
                                    failover or failoverPriority can be set.'
                                  items:
                                    properties:
                                      from:
                                        description: Originating locality, '/' separated,
                                          e.g.
                                        type: string
                                      to:
                                        additionalProperties:
                                          maximum: 4294967295
                                          minimum: 0
                                          type: integer
                                        description: Map of upstream localities to
                                          traffic distribution weights.
                                        type: object
                                    type: object
                                  type: array
                                enabled:
                                  description: enable locality load balancing, this
                                    is DestinationRule-level and will override mesh
                                    wide settings in entirety.
                                  nullable: true
                                  type: boolean
                                failover:
                                  description: 'Optional: only one of distribute,
                                    failover or failoverPriority can be set.'
                                  items:
                                    properties:
                                      from:
                                        description: Originating region.
                                        type: string
                                      to:
                                        description: Destination region the traffic
                                          will fail over to when endpoints in the
                                          'from' region becomes unhealthy.
                                        type: string
                                    type: object
                                  type: array
                                failoverPriority:
                                  description: failoverPriority is an ordered list
                                    of labels used to sort endpoints to do priority
                                    based load balancing.
                                  items:
                                    type: string
                                  type: array
                              type: object
                            simple:
                              description: |2-


                                Valid Options: LEAST_CONN, RANDOM, PASSTHROUGH, ROUND_ROBIN, LEAST_REQUEST
                              enum:
                              - UNSPECIFIED
                              - LEAST_CONN
                              - RANDOM
                              - PASSTHROUGH
                              - ROUND_ROBIN
                              - LEAST_REQUEST
                              type: string
                            warmupDurationSecs:
                              description: Represents the warmup duration of Service.
                              type: string
                              x-kubernetes-validations:
                              - message: must be a valid duration greater than 1ms
                                rule: duration(self) >= duration('1ms')
                          type: object
                        outlierDetection:
                          properties:
                            baseEjectionTime:
                              description: Minimum ejection duration.
                              type: string
                              x-kubernetes-validations:
                              - message: must be a valid duration greater than 1ms
                                rule: duration(self) >= duration('1ms')
                            consecutive5xxErrors:
                              description: Number of 5xx errors before a host is ejected
                                from the connection pool.
                              maximum: 4294967295
                              minimum: 0
                              nullable: true
                              type: integer
                            consecutiveErrors:
                              format: int32
                              type: integer
                            consecutiveGatewayErrors:
                              description: Number of gateway errors before a host
                                is ejected from the connection pool.
                              maximum: 4294967295
                              minimum: 0
                              nullable: true
                              type: integer
                            consecutiveLocalOriginFailures:
                              description: The number of consecutive locally originated
                                failures before ejection occurs.
                              maximum: 4294967295
                              minimum: 0
                              nullable: true
                              type: integer
                            interval:
                              description: Time interval between ejection sweep analysis.
                              type: string
                              x-kubernetes-validations:
                              - message: must be a valid duration greater than 1ms
                                rule: duration(self) >= duration('1ms')
                            maxEjectionPercent:
                              description: Maximum % of hosts in the load balancing
                                pool for the upstream service that can be ejected.
                              format: int32
                              type: integer
                            minHealthPercent:
                              description: Outlier detection will be enabled as long
                                as the associated load balancing pool has at least
                                min_health_percent hosts in healthy mode.
                              format: int32
                              type: integer
                            splitExternalLocalOriginErrors:
                              description: Determines whether to distinguish local
                                origin failures from external errors.
                              type: boolean
                          type: object
                        port:
                          description: Specifies the number of a port on the destination
                            service on which this policy is being applied.
                          properties:
                            number:
                              maximum: 4294967295
                              minimum: 0
                              type: integer
                          type: object
                        tls:
                          description: TLS related settings for connections to the
                            upstream service.
                          properties:
                            caCertificates:
                              description: 'OPTIONAL: The path to the file containing
                                certificate authority certificates to use in verifying
                                a presented server certificate.'
                              type: string
                            caCrl:
                              description: 'OPTIONAL: The path to the file containing
                                the certificate revocation list (CRL) to use in verifying
                                a presented server certificate.'
                              type: string
                            clientCertificate:
                              description: REQUIRED if mode is `MUTUAL`.
                              type: string
                            credentialName:
                              description: The name of the secret that holds the TLS
                                certs for the client including the CA certificates.
                              type: string
                            insecureSkipVerify:
                              description: '`insecureSkipVerify` specifies whether
                                the proxy should skip verifying the CA signature and
                                SAN for the server certificate corresponding to the
                                host.'
                              nullable: true
                              type: boolean
                            mode:
                              description: |-
                                Indicates whether connections to this port should be secured using TLS.

                                Valid Options: DISABLE, SIMPLE, MUTUAL, ISTIO_MUTUAL
                              enum:
                              - DISABLE
                              - SIMPLE
                              - MUTUAL
                              - ISTIO_MUTUAL
                              type: string
                            privateKey:
                              description: REQUIRED if mode is `MUTUAL`.
                              type: string
                            sni:
                              description: SNI string to present to the server during
                                TLS handshake.
                              type: string
                            subjectAltNames:
                              description: A list of alternate names to verify the
                                subject identity in the certificate.
                              items:
                                type: string
                              type: array
                          type: object
                      type: object
                    maxItems: 4096
                    type: array
                  proxyProtocol:
                    description: The upstream PROXY protocol settings.
                    properties:
                      version:
                        description: |-
                          The PROXY protocol version to use.

                          Valid Options: V1, V2
                        enum:
                        - V1
                        - V2
                        type: string
                    type: object
                  tls:
                    description: TLS related settings for connections to the upstream
                      service.
                    properties:
                      caCertificates:
                        description: 'OPTIONAL: The path to the file containing certificate
                          authority certificates to use in verifying a presented server
                          certificate.'
                        type: string
                      caCrl:
                        description: 'OPTIONAL: The path to the file containing the
                          certificate revocation list (CRL) to use in verifying a
                          presented server certificate.'
                        type: string
                      clientCertificate:
                        description: REQUIRED if mode is `MUTUAL`.
                        type: string
                      credentialName:
                        description: The name of the secret that holds the TLS certs
                          for the client including the CA certificates.
                        type: string
                      insecureSkipVerify:
                        description: '`insecureSkipVerify` specifies whether the proxy
                          should skip verifying the CA signature and SAN for the server
                          certificate corresponding to the host.'
                        nullable: true
                        type: boolean
                      mode:
                        description: |-
                          Indicates whether connections to this port should be secured using TLS.

                          Valid Options: DISABLE, SIMPLE, MUTUAL, ISTIO_MUTUAL
                        enum:
                        - DISABLE
                        - SIMPLE
                        - MUTUAL
                        - ISTIO_MUTUAL
                        type: string
                      privateKey:
                        description: REQUIRED if mode is `MUTUAL`.
                        type: string
                      sni:
                        description: SNI string to present to the server during TLS
                          handshake.
                        type: string
                      subjectAltNames:
                        description: A list of alternate names to verify the subject
                          identity in the certificate.
                        items:
                          type: string
                        type: array
                    type: object
                  tunnel:
                    description: Configuration of tunneling TCP over other transport
                      or application layers for the host configured in the DestinationRule.
                    properties:
                      protocol:
                        description: Specifies which protocol to use for tunneling
                          the downstream connection.
                        type: string
                      targetHost:
                        description: Specifies a host to which the downstream connection
                          is tunneled.
                        type: string
                      targetPort:
                        description: Specifies a port to which the downstream connection
                          is tunneled.
                        maximum: 4294967295
                        minimum: 0
                        type: integer
                    required:
                    - targetHost
                    - targetPort
                    type: object
                type: object
              workloadSelector:
                description: Criteria used to select the specific set of pods/VMs
                  on which this `DestinationRule` configuration should be applied.
                properties:
                  matchLabels:
                    additionalProperties:
                      maxLength: 63
                      type: string
                      x-kubernetes-validations:
                      - message: wildcard not allowed in label value match
                        rule: '!self.contains(''*'')'
                    description: One or more labels that indicate a specific set of
                      pods/VMs on which a policy should be applied.
                    maxProperties: 4096
                    type: object
                    x-kubernetes-validations:
                    - message: wildcard not allowed in label key match
                      rule: self.all(key, !key.contains('*'))
                    - message: key must not be empty
                      rule: self.all(key, key.size() != 0)
                type: object
            required:
            - host
            type: object
          status:
            type: object
            x-kubernetes-preserve-unknown-fields: true
        type: object
    served: true
    storage: false
    subresources:
      status: {}
  - additionalPrinterColumns:
    - description: The name of a service from the service registry
      jsonPath: .spec.host
      name: Host
      type: string
    - description: 'CreationTimestamp is a timestamp representing the server time
        when this object was created. It is not guaranteed to be set in happens-before
        order across separate operations. Clients may not set this value. It is represented
        in RFC3339 form and is in UTC. Populated by the system. Read-only. Null for
        lists. More info: https://git.k8s.io/community/contributors/devel/api-conventions.md#metadata'
      jsonPath: .metadata.creationTimestamp
      name: Age
      type: date
    name: v1alpha3
    schema:
      openAPIV3Schema:
        properties:
          spec:
            description: 'Configuration affecting load balancing, outlier detection,
              etc. See more details at: https://istio.io/docs/reference/config/networking/destination-rule.html'
            properties:
              exportTo:
                description: A list of namespaces to which this destination rule is
                  exported.
                items:
                  type: string
                type: array
              host:
                description: The name of a service from the service registry.
                type: string
              subsets:
                description: One or more named sets that represent individual versions
                  of a service.
                items:
                  properties:
                    labels:
                      additionalProperties:
                        type: string
                      description: Labels apply a filter over the endpoints of a service
                        in the service registry.
                      type: object
                    name:
                      description: Name of the subset.
                      type: string
                    trafficPolicy:
                      description: Traffic policies that apply to this subset.
                      properties:
                        connectionPool:
                          properties:
                            http:
                              description: HTTP connection pool settings.
                              properties:
                                h2UpgradePolicy:
                                  description: |-
                                    Specify if http1.1 connection should be upgraded to http2 for the associated destination.

                                    Valid Options: DEFAULT, DO_NOT_UPGRADE, UPGRADE
                                  enum:
                                  - DEFAULT
                                  - DO_NOT_UPGRADE
                                  - UPGRADE
                                  type: string
                                http1MaxPendingRequests:
                                  description: Maximum number of requests that will
                                    be queued while waiting for a ready connection
                                    pool connection.
                                  format: int32
                                  type: integer
                                http2MaxRequests:
                                  description: Maximum number of active requests to
                                    a destination.
                                  format: int32
                                  type: integer
                                idleTimeout:
                                  description: The idle timeout for upstream connection
                                    pool connections.
                                  type: string
                                  x-kubernetes-validations:
                                  - message: must be a valid duration greater than
                                      1ms
                                    rule: duration(self) >= duration('1ms')
                                maxConcurrentStreams:
                                  description: The maximum number of concurrent streams
                                    allowed for a peer on one HTTP/2 connection.
                                  format: int32
                                  type: integer
                                maxRequestsPerConnection:
                                  description: Maximum number of requests per connection
                                    to a backend.
                                  format: int32
                                  type: integer
                                maxRetries:
                                  description: Maximum number of retries that can
                                    be outstanding to all hosts in a cluster at a
                                    given time.
                                  format: int32
                                  type: integer
                                useClientProtocol:
                                  description: If set to true, client protocol will
                                    be preserved while initiating connection to backend.
                                  type: boolean
                              type: object
                            tcp:
                              description: Settings common to both HTTP and TCP upstream
                                connections.
                              properties:
                                connectTimeout:
                                  description: TCP connection timeout.
                                  type: string
                                  x-kubernetes-validations:
                                  - message: must be a valid duration greater than
                                      1ms
                                    rule: duration(self) >= duration('1ms')
                                idleTimeout:
                                  description: The idle timeout for TCP connections.
                                  type: string
                                  x-kubernetes-validations:
                                  - message: must be a valid duration greater than
                                      1ms
                                    rule: duration(self) >= duration('1ms')
                                maxConnectionDuration:
                                  description: The maximum duration of a connection.
                                  type: string
                                  x-kubernetes-validations:
                                  - message: must be a valid duration greater than
                                      1ms
                                    rule: duration(self) >= duration('1ms')
                                maxConnections:
                                  description: Maximum number of HTTP1 /TCP connections
                                    to a destination host.
                                  format: int32
                                  type: integer
                                tcpKeepalive:
                                  description: If set then set SO_KEEPALIVE on the
                                    socket to enable TCP Keepalives.
                                  properties:
                                    interval:
                                      description: The time duration between keep-alive
                                        probes.
                                      type: string
                                      x-kubernetes-validations:
                                      - message: must be a valid duration greater
                                          than 1ms
                                        rule: duration(self) >= duration('1ms')
                                    probes:
                                      description: Maximum number of keepalive probes
                                        to send without response before deciding the
                                        connection is dead.
                                      maximum: 4294967295
                                      minimum: 0
                                      type: integer
                                    time:
                                      description: The time duration a connection
                                        needs to be idle before keep-alive probes
                                        start being sent.
                                      type: string
                                      x-kubernetes-validations:
                                      - message: must be a valid duration greater
                                          than 1ms
                                        rule: duration(self) >= duration('1ms')
                                  type: object
                              type: object
                          type: object
                        loadBalancer:
                          description: Settings controlling the load balancer algorithms.
                          oneOf:
                          - not:
                              anyOf:
                              - required:
                                - simple
                              - required:
                                - consistentHash
                          - required:
                            - simple
                          - required:
                            - consistentHash
                          properties:
                            consistentHash:
                              allOf:
                              - oneOf:
                                - not:
                                    anyOf:
                                    - required:
                                      - httpHeaderName
                                    - required:
                                      - httpCookie
                                    - required:
                                      - useSourceIp
                                    - required:
                                      - httpQueryParameterName
                                - required:
                                  - httpHeaderName
                                - required:
                                  - httpCookie
                                - required:
                                  - useSourceIp
                                - required:
                                  - httpQueryParameterName
                              - oneOf:
                                - not:
                                    anyOf:
                                    - required:
                                      - ringHash
                                    - required:
                                      - maglev
                                - required:
                                  - ringHash
                                - required:
                                  - maglev
                              properties:
                                httpCookie:
                                  description: Hash based on HTTP cookie.
                                  properties:
                                    name:
                                      description: Name of the cookie.
                                      type: string
                                    path:
                                      description: Path to set for the cookie.
                                      type: string
                                    ttl:
                                      description: Lifetime of the cookie.
                                      type: string
                                  required:
                                  - name
                                  type: object
                                httpHeaderName:
                                  description: Hash based on a specific HTTP header.
                                  type: string
                                httpQueryParameterName:
                                  description: Hash based on a specific HTTP query
                                    parameter.
                                  type: string
                                maglev:
                                  description: The Maglev load balancer implements
                                    consistent hashing to backend hosts.
                                  properties:
                                    tableSize:
                                      description: The table size for Maglev hashing.
                                      minimum: 0
                                      type: integer
                                  type: object
                                minimumRingSize:
                                  description: Deprecated.
                                  minimum: 0
                                  type: integer
                                ringHash:
                                  description: The ring/modulo hash load balancer
                                    implements consistent hashing to backend hosts.
                                  properties:
                                    minimumRingSize:
                                      description: The minimum number of virtual nodes
                                        to use for the hash ring.
                                      minimum: 0
                                      type: integer
                                  type: object
                                useSourceIp:
                                  description: Hash based on the source IP address.
                                  type: boolean
                              type: object
                            localityLbSetting:
                              properties:
                                distribute:
                                  description: 'Optional: only one of distribute,
                                    failover or failoverPriority can be set.'
                                  items:
                                    properties:
                                      from:
                                        description: Originating locality, '/' separated,
                                          e.g.
                                        type: string
                                      to:
                                        additionalProperties:
                                          maximum: 4294967295
                                          minimum: 0
                                          type: integer
                                        description: Map of upstream localities to
                                          traffic distribution weights.
                                        type: object
                                    type: object
                                  type: array
                                enabled:
                                  description: enable locality load balancing, this
                                    is DestinationRule-level and will override mesh
                                    wide settings in entirety.
                                  nullable: true
                                  type: boolean
                                failover:
                                  description: 'Optional: only one of distribute,
                                    failover or failoverPriority can be set.'
                                  items:
                                    properties:
                                      from:
                                        description: Originating region.
                                        type: string
                                      to:
                                        description: Destination region the traffic
                                          will fail over to when endpoints in the
                                          'from' region becomes unhealthy.
                                        type: string
                                    type: object
                                  type: array
                                failoverPriority:
                                  description: failoverPriority is an ordered list
                                    of labels used to sort endpoints to do priority
                                    based load balancing.
                                  items:
                                    type: string
                                  type: array
                              type: object
                            simple:
                              description: |2-


                                Valid Options: LEAST_CONN, RANDOM, PASSTHROUGH, ROUND_ROBIN, LEAST_REQUEST
                              enum:
                              - UNSPECIFIED
                              - LEAST_CONN
                              - RANDOM
                              - PASSTHROUGH
                              - ROUND_ROBIN
                              - LEAST_REQUEST
                              type: string
                            warmupDurationSecs:
                              description: Represents the warmup duration of Service.
                              type: string
                              x-kubernetes-validations:
                              - message: must be a valid duration greater than 1ms
                                rule: duration(self) >= duration('1ms')
                          type: object
                        outlierDetection:
                          properties:
                            baseEjectionTime:
                              description: Minimum ejection duration.
                              type: string
                              x-kubernetes-validations:
                              - message: must be a valid duration greater than 1ms
                                rule: duration(self) >= duration('1ms')
                            consecutive5xxErrors:
                              description: Number of 5xx errors before a host is ejected
                                from the connection pool.
                              maximum: 4294967295
                              minimum: 0
                              nullable: true
                              type: integer
                            consecutiveErrors:
                              format: int32
                              type: integer
                            consecutiveGatewayErrors:
                              description: Number of gateway errors before a host
                                is ejected from the connection pool.
                              maximum: 4294967295
                              minimum: 0
                              nullable: true
                              type: integer
                            consecutiveLocalOriginFailures:
                              description: The number of consecutive locally originated
                                failures before ejection occurs.
                              maximum: 4294967295
                              minimum: 0
                              nullable: true
                              type: integer
                            interval:
                              description: Time interval between ejection sweep analysis.
                              type: string
                              x-kubernetes-validations:
                              - message: must be a valid duration greater than 1ms
                                rule: duration(self) >= duration('1ms')
                            maxEjectionPercent:
                              description: Maximum % of hosts in the load balancing
                                pool for the upstream service that can be ejected.
                              format: int32
                              type: integer
                            minHealthPercent:
                              description: Outlier detection will be enabled as long
                                as the associated load balancing pool has at least
                                min_health_percent hosts in healthy mode.
                              format: int32
                              type: integer
                            splitExternalLocalOriginErrors:
                              description: Determines whether to distinguish local
                                origin failures from external errors.
                              type: boolean
                          type: object
                        portLevelSettings:
                          description: Traffic policies specific to individual ports.
                          items:
                            properties:
                              connectionPool:
                                properties:
                                  http:
                                    description: HTTP connection pool settings.
                                    properties:
                                      h2UpgradePolicy:
                                        description: |-
                                          Specify if http1.1 connection should be upgraded to http2 for the associated destination.

                                          Valid Options: DEFAULT, DO_NOT_UPGRADE, UPGRADE
                                        enum:
                                        - DEFAULT
                                        - DO_NOT_UPGRADE
                                        - UPGRADE
                                        type: string
                                      http1MaxPendingRequests:
                                        description: Maximum number of requests that
                                          will be queued while waiting for a ready
                                          connection pool connection.
                                        format: int32
                                        type: integer
                                      http2MaxRequests:
                                        description: Maximum number of active requests
                                          to a destination.
                                        format: int32
                                        type: integer
                                      idleTimeout:
                                        description: The idle timeout for upstream
                                          connection pool connections.
                                        type: string
                                        x-kubernetes-validations:
                                        - message: must be a valid duration greater
                                            than 1ms
                                          rule: duration(self) >= duration('1ms')
                                      maxConcurrentStreams:
                                        description: The maximum number of concurrent
                                          streams allowed for a peer on one HTTP/2
                                          connection.
                                        format: int32
                                        type: integer
                                      maxRequestsPerConnection:
                                        description: Maximum number of requests per
                                          connection to a backend.
                                        format: int32
                                        type: integer
                                      maxRetries:
                                        description: Maximum number of retries that
                                          can be outstanding to all hosts in a cluster
                                          at a given time.
                                        format: int32
                                        type: integer
                                      useClientProtocol:
                                        description: If set to true, client protocol
                                          will be preserved while initiating connection
                                          to backend.
                                        type: boolean
                                    type: object
                                  tcp:
                                    description: Settings common to both HTTP and
                                      TCP upstream connections.
                                    properties:
                                      connectTimeout:
                                        description: TCP connection timeout.
                                        type: string
                                        x-kubernetes-validations:
                                        - message: must be a valid duration greater
                                            than 1ms
                                          rule: duration(self) >= duration('1ms')
                                      idleTimeout:
                                        description: The idle timeout for TCP connections.
                                        type: string
                                        x-kubernetes-validations:
                                        - message: must be a valid duration greater
                                            than 1ms
                                          rule: duration(self) >= duration('1ms')
                                      maxConnectionDuration:
                                        description: The maximum duration of a connection.
                                        type: string
                                        x-kubernetes-validations:
                                        - message: must be a valid duration greater
                                            than 1ms
                                          rule: duration(self) >= duration('1ms')
                                      maxConnections:
                                        description: Maximum number of HTTP1 /TCP
                                          connections to a destination host.
                                        format: int32
                                        type: integer
                                      tcpKeepalive:
                                        description: If set then set SO_KEEPALIVE
                                          on the socket to enable TCP Keepalives.
                                        properties:
                                          interval:
                                            description: The time duration between
                                              keep-alive probes.
                                            type: string
                                            x-kubernetes-validations:
                                            - message: must be a valid duration greater
                                                than 1ms
                                              rule: duration(self) >= duration('1ms')
                                          probes:
                                            description: Maximum number of keepalive
                                              probes to send without response before
                                              deciding the connection is dead.
                                            maximum: 4294967295
                                            minimum: 0
                                            type: integer
                                          time:
                                            description: The time duration a connection
                                              needs to be idle before keep-alive probes
                                              start being sent.
                                            type: string
                                            x-kubernetes-validations:
                                            - message: must be a valid duration greater
                                                than 1ms
                                              rule: duration(self) >= duration('1ms')
                                        type: object
                                    type: object
                                type: object
                              loadBalancer:
                                description: Settings controlling the load balancer
                                  algorithms.
                                oneOf:
                                - not:
                                    anyOf:
                                    - required:
                                      - simple
                                    - required:
                                      - consistentHash
                                - required:
                                  - simple
                                - required:
                                  - consistentHash
                                properties:
                                  consistentHash:
                                    allOf:
                                    - oneOf:
                                      - not:
                                          anyOf:
                                          - required:
                                            - httpHeaderName
                                          - required:
                                            - httpCookie
                                          - required:
                                            - useSourceIp
                                          - required:
                                            - httpQueryParameterName
                                      - required:
                                        - httpHeaderName
                                      - required:
                                        - httpCookie
                                      - required:
                                        - useSourceIp
                                      - required:
                                        - httpQueryParameterName
                                    - oneOf:
                                      - not:
                                          anyOf:
                                          - required:
                                            - ringHash
                                          - required:
                                            - maglev
                                      - required:
                                        - ringHash
                                      - required:
                                        - maglev
                                    properties:
                                      httpCookie:
                                        description: Hash based on HTTP cookie.
                                        properties:
                                          name:
                                            description: Name of the cookie.
                                            type: string
                                          path:
                                            description: Path to set for the cookie.
                                            type: string
                                          ttl:
                                            description: Lifetime of the cookie.
                                            type: string
                                        required:
                                        - name
                                        type: object
                                      httpHeaderName:
                                        description: Hash based on a specific HTTP
                                          header.
                                        type: string
                                      httpQueryParameterName:
                                        description: Hash based on a specific HTTP
                                          query parameter.
                                        type: string
                                      maglev:
                                        description: The Maglev load balancer implements
                                          consistent hashing to backend hosts.
                                        properties:
                                          tableSize:
                                            description: The table size for Maglev
                                              hashing.
                                            minimum: 0
                                            type: integer
                                        type: object
                                      minimumRingSize:
                                        description: Deprecated.
                                        minimum: 0
                                        type: integer
                                      ringHash:
                                        description: The ring/modulo hash load balancer
                                          implements consistent hashing to backend
                                          hosts.
                                        properties:
                                          minimumRingSize:
                                            description: The minimum number of virtual
                                              nodes to use for the hash ring.
                                            minimum: 0
                                            type: integer
                                        type: object
                                      useSourceIp:
                                        description: Hash based on the source IP address.
                                        type: boolean
                                    type: object
                                  localityLbSetting:
                                    properties:
                                      distribute:
                                        description: 'Optional: only one of distribute,
                                          failover or failoverPriority can be set.'
                                        items:
                                          properties:
                                            from:
                                              description: Originating locality, '/'
                                                separated, e.g.
                                              type: string
                                            to:
                                              additionalProperties:
                                                maximum: 4294967295
                                                minimum: 0
                                                type: integer
                                              description: Map of upstream localities
                                                to traffic distribution weights.
                                              type: object
                                          type: object
                                        type: array
                                      enabled:
                                        description: enable locality load balancing,
                                          this is DestinationRule-level and will override
                                          mesh wide settings in entirety.
                                        nullable: true
                                        type: boolean
                                      failover:
                                        description: 'Optional: only one of distribute,
                                          failover or failoverPriority can be set.'
                                        items:
                                          properties:
                                            from:
                                              description: Originating region.
                                              type: string
                                            to:
                                              description: Destination region the
                                                traffic will fail over to when endpoints
                                                in the 'from' region becomes unhealthy.
                                              type: string
                                          type: object
                                        type: array
                                      failoverPriority:
                                        description: failoverPriority is an ordered
                                          list of labels used to sort endpoints to
                                          do priority based load balancing.
                                        items:
                                          type: string
                                        type: array
                                    type: object
                                  simple:
                                    description: |2-


                                      Valid Options: LEAST_CONN, RANDOM, PASSTHROUGH, ROUND_ROBIN, LEAST_REQUEST
                                    enum:
                                    - UNSPECIFIED
                                    - LEAST_CONN
                                    - RANDOM
                                    - PASSTHROUGH
                                    - ROUND_ROBIN
                                    - LEAST_REQUEST
                                    type: string
                                  warmupDurationSecs:
                                    description: Represents the warmup duration of
                                      Service.
                                    type: string
                                    x-kubernetes-validations:
                                    - message: must be a valid duration greater than
                                        1ms
                                      rule: duration(self) >= duration('1ms')
                                type: object
                              outlierDetection:
                                properties:
                                  baseEjectionTime:
                                    description: Minimum ejection duration.
                                    type: string
                                    x-kubernetes-validations:
                                    - message: must be a valid duration greater than
                                        1ms
                                      rule: duration(self) >= duration('1ms')
                                  consecutive5xxErrors:
                                    description: Number of 5xx errors before a host
                                      is ejected from the connection pool.
                                    maximum: 4294967295
                                    minimum: 0
                                    nullable: true
                                    type: integer
                                  consecutiveErrors:
                                    format: int32
                                    type: integer
                                  consecutiveGatewayErrors:
                                    description: Number of gateway errors before a
                                      host is ejected from the connection pool.
                                    maximum: 4294967295
                                    minimum: 0
                                    nullable: true
                                    type: integer
                                  consecutiveLocalOriginFailures:
                                    description: The number of consecutive locally
                                      originated failures before ejection occurs.
                                    maximum: 4294967295
                                    minimum: 0
                                    nullable: true
                                    type: integer
                                  interval:
                                    description: Time interval between ejection sweep
                                      analysis.
                                    type: string
                                    x-kubernetes-validations:
                                    - message: must be a valid duration greater than
                                        1ms
                                      rule: duration(self) >= duration('1ms')
                                  maxEjectionPercent:
                                    description: Maximum % of hosts in the load balancing
                                      pool for the upstream service that can be ejected.
                                    format: int32
                                    type: integer
                                  minHealthPercent:
                                    description: Outlier detection will be enabled
                                      as long as the associated load balancing pool
                                      has at least min_health_percent hosts in healthy
                                      mode.
                                    format: int32
                                    type: integer
                                  splitExternalLocalOriginErrors:
                                    description: Determines whether to distinguish
                                      local origin failures from external errors.
                                    type: boolean
                                type: object
                              port:
                                description: Specifies the number of a port on the
                                  destination service on which this policy is being
                                  applied.
                                properties:
                                  number:
                                    maximum: 4294967295
                                    minimum: 0
                                    type: integer
                                type: object
                              tls:
                                description: TLS related settings for connections
                                  to the upstream service.
                                properties:
                                  caCertificates:
                                    description: 'OPTIONAL: The path to the file containing
                                      certificate authority certificates to use in
                                      verifying a presented server certificate.'
                                    type: string
                                  caCrl:
                                    description: 'OPTIONAL: The path to the file containing
                                      the certificate revocation list (CRL) to use
                                      in verifying a presented server certificate.'
                                    type: string
                                  clientCertificate:
                                    description: REQUIRED if mode is `MUTUAL`.
                                    type: string
                                  credentialName:
                                    description: The name of the secret that holds
                                      the TLS certs for the client including the CA
                                      certificates.
                                    type: string
                                  insecureSkipVerify:
                                    description: '`insecureSkipVerify` specifies whether
                                      the proxy should skip verifying the CA signature
                                      and SAN for the server certificate corresponding
                                      to the host.'
                                    nullable: true
                                    type: boolean
                                  mode:
                                    description: |-
                                      Indicates whether connections to this port should be secured using TLS.

                                      Valid Options: DISABLE, SIMPLE, MUTUAL, ISTIO_MUTUAL
                                    enum:
                                    - DISABLE
                                    - SIMPLE
                                    - MUTUAL
                                    - ISTIO_MUTUAL
                                    type: string
                                  privateKey:
                                    description: REQUIRED if mode is `MUTUAL`.
                                    type: string
                                  sni:
                                    description: SNI string to present to the server
                                      during TLS handshake.
                                    type: string
                                  subjectAltNames:
                                    description: A list of alternate names to verify
                                      the subject identity in the certificate.
                                    items:
                                      type: string
                                    type: array
                                type: object
                            type: object
                          maxItems: 4096
                          type: array
                        proxyProtocol:
                          description: The upstream PROXY protocol settings.
                          properties:
                            version:
                              description: |-
                                The PROXY protocol version to use.

                                Valid Options: V1, V2
                              enum:
                              - V1
                              - V2
                              type: string
                          type: object
                        tls:
                          description: TLS related settings for connections to the
                            upstream service.
                          properties:
                            caCertificates:
                              description: 'OPTIONAL: The path to the file containing
                                certificate authority certificates to use in verifying
                                a presented server certificate.'
                              type: string
                            caCrl:
                              description: 'OPTIONAL: The path to the file containing
                                the certificate revocation list (CRL) to use in verifying
                                a presented server certificate.'
                              type: string
                            clientCertificate:
                              description: REQUIRED if mode is `MUTUAL`.
                              type: string
                            credentialName:
                              description: The name of the secret that holds the TLS
                                certs for the client including the CA certificates.
                              type: string
                            insecureSkipVerify:
                              description: '`insecureSkipVerify` specifies whether
                                the proxy should skip verifying the CA signature and
                                SAN for the server certificate corresponding to the
                                host.'
                              nullable: true
                              type: boolean
                            mode:
                              description: |-
                                Indicates whether connections to this port should be secured using TLS.

                                Valid Options: DISABLE, SIMPLE, MUTUAL, ISTIO_MUTUAL
                              enum:
                              - DISABLE
                              - SIMPLE
                              - MUTUAL
                              - ISTIO_MUTUAL
                              type: string
                            privateKey:
                              description: REQUIRED if mode is `MUTUAL`.
                              type: string
                            sni:
                              description: SNI string to present to the server during
                                TLS handshake.
                              type: string
                            subjectAltNames:
                              description: A list of alternate names to verify the
                                subject identity in the certificate.
                              items:
                                type: string
                              type: array
                          type: object
                        tunnel:
                          description: Configuration of tunneling TCP over other transport
                            or application layers for the host configured in the DestinationRule.
                          properties:
                            protocol:
                              description: Specifies which protocol to use for tunneling
                                the downstream connection.
                              type: string
                            targetHost:
                              description: Specifies a host to which the downstream
                                connection is tunneled.
                              type: string
                            targetPort:
                              description: Specifies a port to which the downstream
                                connection is tunneled.
                              maximum: 4294967295
                              minimum: 0
                              type: integer
                          required:
                          - targetHost
                          - targetPort
                          type: object
                      type: object
                  required:
                  - name
                  type: object
                type: array
              trafficPolicy:
                description: Traffic policies to apply (load balancing policy, connection
                  pool sizes, outlier detection).
                properties:
                  connectionPool:
                    properties:
                      http:
                        description: HTTP connection pool settings.
                        properties:
                          h2UpgradePolicy:
                            description: |-
                              Specify if http1.1 connection should be upgraded to http2 for the associated destination.

                              Valid Options: DEFAULT, DO_NOT_UPGRADE, UPGRADE
                            enum:
                            - DEFAULT
                            - DO_NOT_UPGRADE
                            - UPGRADE
                            type: string
                          http1MaxPendingRequests:
                            description: Maximum number of requests that will be queued
                              while waiting for a ready connection pool connection.
                            format: int32
                            type: integer
                          http2MaxRequests:
                            description: Maximum number of active requests to a destination.
                            format: int32
                            type: integer
                          idleTimeout:
                            description: The idle timeout for upstream connection
                              pool connections.
                            type: string
                            x-kubernetes-validations:
                            - message: must be a valid duration greater than 1ms
                              rule: duration(self) >= duration('1ms')
                          maxConcurrentStreams:
                            description: The maximum number of concurrent streams
                              allowed for a peer on one HTTP/2 connection.
                            format: int32
                            type: integer
                          maxRequestsPerConnection:
                            description: Maximum number of requests per connection
                              to a backend.
                            format: int32
                            type: integer
                          maxRetries:
                            description: Maximum number of retries that can be outstanding
                              to all hosts in a cluster at a given time.
                            format: int32
                            type: integer
                          useClientProtocol:
                            description: If set to true, client protocol will be preserved
                              while initiating connection to backend.
                            type: boolean
                        type: object
                      tcp:
                        description: Settings common to both HTTP and TCP upstream
                          connections.
                        properties:
                          connectTimeout:
                            description: TCP connection timeout.
                            type: string
                            x-kubernetes-validations:
                            - message: must be a valid duration greater than 1ms
                              rule: duration(self) >= duration('1ms')
                          idleTimeout:
                            description: The idle timeout for TCP connections.
                            type: string
                            x-kubernetes-validations:
                            - message: must be a valid duration greater than 1ms
                              rule: duration(self) >= duration('1ms')
                          maxConnectionDuration:
                            description: The maximum duration of a connection.
                            type: string
                            x-kubernetes-validations:
                            - message: must be a valid duration greater than 1ms
                              rule: duration(self) >= duration('1ms')
                          maxConnections:
                            description: Maximum number of HTTP1 /TCP connections
                              to a destination host.
                            format: int32
                            type: integer
                          tcpKeepalive:
                            description: If set then set SO_KEEPALIVE on the socket
                              to enable TCP Keepalives.
                            properties:
                              interval:
                                description: The time duration between keep-alive
                                  probes.
                                type: string
                                x-kubernetes-validations:
                                - message: must be a valid duration greater than 1ms
                                  rule: duration(self) >= duration('1ms')
                              probes:
                                description: Maximum number of keepalive probes to
                                  send without response before deciding the connection
                                  is dead.
                                maximum: 4294967295
                                minimum: 0
                                type: integer
                              time:
                                description: The time duration a connection needs
                                  to be idle before keep-alive probes start being
                                  sent.
                                type: string
                                x-kubernetes-validations:
                                - message: must be a valid duration greater than 1ms
                                  rule: duration(self) >= duration('1ms')
                            type: object
                        type: object
                    type: object
                  loadBalancer:
                    description: Settings controlling the load balancer algorithms.
                    oneOf:
                    - not:
                        anyOf:
                        - required:
                          - simple
                        - required:
                          - consistentHash
                    - required:
                      - simple
                    - required:
                      - consistentHash
                    properties:
                      consistentHash:
                        allOf:
                        - oneOf:
                          - not:
                              anyOf:
                              - required:
                                - httpHeaderName
                              - required:
                                - httpCookie
                              - required:
                                - useSourceIp
                              - required:
                                - httpQueryParameterName
                          - required:
                            - httpHeaderName
                          - required:
                            - httpCookie
                          - required:
                            - useSourceIp
                          - required:
                            - httpQueryParameterName
                        - oneOf:
                          - not:
                              anyOf:
                              - required:
                                - ringHash
                              - required:
                                - maglev
                          - required:
                            - ringHash
                          - required:
                            - maglev
                        properties:
                          httpCookie:
                            description: Hash based on HTTP cookie.
                            properties:
                              name:
                                description: Name of the cookie.
                                type: string
                              path:
                                description: Path to set for the cookie.
                                type: string
                              ttl:
                                description: Lifetime of the cookie.
                                type: string
                            required:
                            - name
                            type: object
                          httpHeaderName:
                            description: Hash based on a specific HTTP header.
                            type: string
                          httpQueryParameterName:
                            description: Hash based on a specific HTTP query parameter.
                            type: string
                          maglev:
                            description: The Maglev load balancer implements consistent
                              hashing to backend hosts.
                            properties:
                              tableSize:
                                description: The table size for Maglev hashing.
                                minimum: 0
                                type: integer
                            type: object
                          minimumRingSize:
                            description: Deprecated.
                            minimum: 0
                            type: integer
                          ringHash:
                            description: The ring/modulo hash load balancer implements
                              consistent hashing to backend hosts.
                            properties:
                              minimumRingSize:
                                description: The minimum number of virtual nodes to
                                  use for the hash ring.
                                minimum: 0
                                type: integer
                            type: object
                          useSourceIp:
                            description: Hash based on the source IP address.
                            type: boolean
                        type: object
                      localityLbSetting:
                        properties:
                          distribute:
                            description: 'Optional: only one of distribute, failover
                              or failoverPriority can be set.'
                            items:
                              properties:
                                from:
                                  description: Originating locality, '/' separated,
                                    e.g.
                                  type: string
                                to:
                                  additionalProperties:
                                    maximum: 4294967295
                                    minimum: 0
                                    type: integer
                                  description: Map of upstream localities to traffic
                                    distribution weights.
                                  type: object
                              type: object
                            type: array
                          enabled:
                            description: enable locality load balancing, this is DestinationRule-level
                              and will override mesh wide settings in entirety.
                            nullable: true
                            type: boolean
                          failover:
                            description: 'Optional: only one of distribute, failover
                              or failoverPriority can be set.'
                            items:
                              properties:
                                from:
                                  description: Originating region.
                                  type: string
                                to:
                                  description: Destination region the traffic will
                                    fail over to when endpoints in the 'from' region
                                    becomes unhealthy.
                                  type: string
                              type: object
                            type: array
                          failoverPriority:
                            description: failoverPriority is an ordered list of labels
                              used to sort endpoints to do priority based load balancing.
                            items:
                              type: string
                            type: array
                        type: object
                      simple:
                        description: |2-


                          Valid Options: LEAST_CONN, RANDOM, PASSTHROUGH, ROUND_ROBIN, LEAST_REQUEST
                        enum:
                        - UNSPECIFIED
                        - LEAST_CONN
                        - RANDOM
                        - PASSTHROUGH
                        - ROUND_ROBIN
                        - LEAST_REQUEST
                        type: string
                      warmupDurationSecs:
                        description: Represents the warmup duration of Service.
                        type: string
                        x-kubernetes-validations:
                        - message: must be a valid duration greater than 1ms
                          rule: duration(self) >= duration('1ms')
                    type: object
                  outlierDetection:
                    properties:
                      baseEjectionTime:
                        description: Minimum ejection duration.
                        type: string
                        x-kubernetes-validations:
                        - message: must be a valid duration greater than 1ms
                          rule: duration(self) >= duration('1ms')
                      consecutive5xxErrors:
                        description: Number of 5xx errors before a host is ejected
                          from the connection pool.
                        maximum: 4294967295
                        minimum: 0
                        nullable: true
                        type: integer
                      consecutiveErrors:
                        format: int32
                        type: integer
                      consecutiveGatewayErrors:
                        description: Number of gateway errors before a host is ejected
                          from the connection pool.
                        maximum: 4294967295
                        minimum: 0
                        nullable: true
                        type: integer
                      consecutiveLocalOriginFailures:
                        description: The number of consecutive locally originated
                          failures before ejection occurs.
                        maximum: 4294967295
                        minimum: 0
                        nullable: true
                        type: integer
                      interval:
                        description: Time interval between ejection sweep analysis.
                        type: string
                        x-kubernetes-validations:
                        - message: must be a valid duration greater than 1ms
                          rule: duration(self) >= duration('1ms')
                      maxEjectionPercent:
                        description: Maximum % of hosts in the load balancing pool
                          for the upstream service that can be ejected.
                        format: int32
                        type: integer
                      minHealthPercent:
                        description: Outlier detection will be enabled as long as
                          the associated load balancing pool has at least min_health_percent
                          hosts in healthy mode.
                        format: int32
                        type: integer
                      splitExternalLocalOriginErrors:
                        description: Determines whether to distinguish local origin
                          failures from external errors.
                        type: boolean
                    type: object
                  portLevelSettings:
                    description: Traffic policies specific to individual ports.
                    items:
                      properties:
                        connectionPool:
                          properties:
                            http:
                              description: HTTP connection pool settings.
                              properties:
                                h2UpgradePolicy:
                                  description: |-
                                    Specify if http1.1 connection should be upgraded to http2 for the associated destination.

                                    Valid Options: DEFAULT, DO_NOT_UPGRADE, UPGRADE
                                  enum:
                                  - DEFAULT
                                  - DO_NOT_UPGRADE
                                  - UPGRADE
                                  type: string
                                http1MaxPendingRequests:
                                  description: Maximum number of requests that will
                                    be queued while waiting for a ready connection
                                    pool connection.
                                  format: int32
                                  type: integer
                                http2MaxRequests:
                                  description: Maximum number of active requests to
                                    a destination.
                                  format: int32
                                  type: integer
                                idleTimeout:
                                  description: The idle timeout for upstream connection
                                    pool connections.
                                  type: string
                                  x-kubernetes-validations:
                                  - message: must be a valid duration greater than
                                      1ms
                                    rule: duration(self) >= duration('1ms')
                                maxConcurrentStreams:
                                  description: The maximum number of concurrent streams
                                    allowed for a peer on one HTTP/2 connection.
                                  format: int32
                                  type: integer
                                maxRequestsPerConnection:
                                  description: Maximum number of requests per connection
                                    to a backend.
                                  format: int32
                                  type: integer
                                maxRetries:
                                  description: Maximum number of retries that can
                                    be outstanding to all hosts in a cluster at a
                                    given time.
                                  format: int32
                                  type: integer
                                useClientProtocol:
                                  description: If set to true, client protocol will
                                    be preserved while initiating connection to backend.
                                  type: boolean
                              type: object
                            tcp:
                              description: Settings common to both HTTP and TCP upstream
                                connections.
                              properties:
                                connectTimeout:
                                  description: TCP connection timeout.
                                  type: string
                                  x-kubernetes-validations:
                                  - message: must be a valid duration greater than
                                      1ms
                                    rule: duration(self) >= duration('1ms')
                                idleTimeout:
                                  description: The idle timeout for TCP connections.
                                  type: string
                                  x-kubernetes-validations:
                                  - message: must be a valid duration greater than
                                      1ms
                                    rule: duration(self) >= duration('1ms')
                                maxConnectionDuration:
                                  description: The maximum duration of a connection.
                                  type: string
                                  x-kubernetes-validations:
                                  - message: must be a valid duration greater than
                                      1ms
                                    rule: duration(self) >= duration('1ms')
                                maxConnections:
                                  description: Maximum number of HTTP1 /TCP connections
                                    to a destination host.
                                  format: int32
                                  type: integer
                                tcpKeepalive:
                                  description: If set then set SO_KEEPALIVE on the
                                    socket to enable TCP Keepalives.
                                  properties:
                                    interval:
                                      description: The time duration between keep-alive
                                        probes.
                                      type: string
                                      x-kubernetes-validations:
                                      - message: must be a valid duration greater
                                          than 1ms
                                        rule: duration(self) >= duration('1ms')
                                    probes:
                                      description: Maximum number of keepalive probes
                                        to send without response before deciding the
                                        connection is dead.
                                      maximum: 4294967295
                                      minimum: 0
                                      type: integer
                                    time:
                                      description: The time duration a connection
                                        needs to be idle before keep-alive probes
                                        start being sent.
                                      type: string
                                      x-kubernetes-validations:
                                      - message: must be a valid duration greater
                                          than 1ms
                                        rule: duration(self) >= duration('1ms')
                                  type: object
                              type: object
                          type: object
                        loadBalancer:
                          description: Settings controlling the load balancer algorithms.
                          oneOf:
                          - not:
                              anyOf:
                              - required:
                                - simple
                              - required:
                                - consistentHash
                          - required:
                            - simple
                          - required:
                            - consistentHash
                          properties:
                            consistentHash:
                              allOf:
                              - oneOf:
                                - not:
                                    anyOf:
                                    - required:
                                      - httpHeaderName
                                    - required:
                                      - httpCookie
                                    - required:
                                      - useSourceIp
                                    - required:
                                      - httpQueryParameterName
                                - required:
                                  - httpHeaderName
                                - required:
                                  - httpCookie
                                - required:
                                  - useSourceIp
                                - required:
                                  - httpQueryParameterName
                              - oneOf:
                                - not:
                                    anyOf:
                                    - required:
                                      - ringHash
                                    - required:
                                      - maglev
                                - required:
                                  - ringHash
                                - required:
                                  - maglev
                              properties:
                                httpCookie:
                                  description: Hash based on HTTP cookie.
                                  properties:
                                    name:
                                      description: Name of the cookie.
                                      type: string
                                    path:
                                      description: Path to set for the cookie.
                                      type: string
                                    ttl:
                                      description: Lifetime of the cookie.
                                      type: string
                                  required:
                                  - name
                                  type: object
                                httpHeaderName:
                                  description: Hash based on a specific HTTP header.
                                  type: string
                                httpQueryParameterName:
                                  description: Hash based on a specific HTTP query
                                    parameter.
                                  type: string
                                maglev:
                                  description: The Maglev load balancer implements
                                    consistent hashing to backend hosts.
                                  properties:
                                    tableSize:
                                      description: The table size for Maglev hashing.
                                      minimum: 0
                                      type: integer
                                  type: object
                                minimumRingSize:
                                  description: Deprecated.
                                  minimum: 0
                                  type: integer
                                ringHash:
                                  description: The ring/modulo hash load balancer
                                    implements consistent hashing to backend hosts.
                                  properties:
                                    minimumRingSize:
                                      description: The minimum number of virtual nodes
                                        to use for the hash ring.
                                      minimum: 0
                                      type: integer
                                  type: object
                                useSourceIp:
                                  description: Hash based on the source IP address.
                                  type: boolean
                              type: object
                            localityLbSetting:
                              properties:
                                distribute:
                                  description: 'Optional: only one of distribute,
                                    failover or failoverPriority can be set.'
                                  items:
                                    properties:
                                      from:
                                        description: Originating locality, '/' separated,
                                          e.g.
                                        type: string
                                      to:
                                        additionalProperties:
                                          maximum: 4294967295
                                          minimum: 0
                                          type: integer
                                        description: Map of upstream localities to
                                          traffic distribution weights.
                                        type: object
                                    type: object
                                  type: array
                                enabled:
                                  description: enable locality load balancing, this
                                    is DestinationRule-level and will override mesh
                                    wide settings in entirety.
                                  nullable: true
                                  type: boolean
                                failover:
                                  description: 'Optional: only one of distribute,
                                    failover or failoverPriority can be set.'
                                  items:
                                    properties:
                                      from:
                                        description: Originating region.
                                        type: string
                                      to:
                                        description: Destination region the traffic
                                          will fail over to when endpoints in the
                                          'from' region becomes unhealthy.
                                        type: string
                                    type: object
                                  type: array
                                failoverPriority:
                                  description: failoverPriority is an ordered list
                                    of labels used to sort endpoints to do priority
                                    based load balancing.
                                  items:
                                    type: string
                                  type: array
                              type: object
                            simple:
                              description: |2-


                                Valid Options: LEAST_CONN, RANDOM, PASSTHROUGH, ROUND_ROBIN, LEAST_REQUEST
                              enum:
                              - UNSPECIFIED
                              - LEAST_CONN
                              - RANDOM
                              - PASSTHROUGH
                              - ROUND_ROBIN
                              - LEAST_REQUEST
                              type: string
                            warmupDurationSecs:
                              description: Represents the warmup duration of Service.
                              type: string
                              x-kubernetes-validations:
                              - message: must be a valid duration greater than 1ms
                                rule: duration(self) >= duration('1ms')
                          type: object
                        outlierDetection:
                          properties:
                            baseEjectionTime:
                              description: Minimum ejection duration.
                              type: string
                              x-kubernetes-validations:
                              - message: must be a valid duration greater than 1ms
                                rule: duration(self) >= duration('1ms')
                            consecutive5xxErrors:
                              description: Number of 5xx errors before a host is ejected
                                from the connection pool.
                              maximum: 4294967295
                              minimum: 0
                              nullable: true
                              type: integer
                            consecutiveErrors:
                              format: int32
                              type: integer
                            consecutiveGatewayErrors:
                              description: Number of gateway errors before a host
                                is ejected from the connection pool.
                              maximum: 4294967295
                              minimum: 0
                              nullable: true
                              type: integer
                            consecutiveLocalOriginFailures:
                              description: The number of consecutive locally originated
                                failures before ejection occurs.
                              maximum: 4294967295
                              minimum: 0
                              nullable: true
                              type: integer
                            interval:
                              description: Time interval between ejection sweep analysis.
                              type: string
                              x-kubernetes-validations:
                              - message: must be a valid duration greater than 1ms
                                rule: duration(self) >= duration('1ms')
                            maxEjectionPercent:
                              description: Maximum % of hosts in the load balancing
                                pool for the upstream service that can be ejected.
                              format: int32
                              type: integer
                            minHealthPercent:
                              description: Outlier detection will be enabled as long
                                as the associated load balancing pool has at least
                                min_health_percent hosts in healthy mode.
                              format: int32
                              type: integer
                            splitExternalLocalOriginErrors:
                              description: Determines whether to distinguish local
                                origin failures from external errors.
                              type: boolean
                          type: object
                        port:
                          description: Specifies the number of a port on the destination
                            service on which this policy is being applied.
                          properties:
                            number:
                              maximum: 4294967295
                              minimum: 0
                              type: integer
                          type: object
                        tls:
                          description: TLS related settings for connections to the
                            upstream service.
                          properties:
                            caCertificates:
                              description: 'OPTIONAL: The path to the file containing
                                certificate authority certificates to use in verifying
                                a presented server certificate.'
                              type: string
                            caCrl:
                              description: 'OPTIONAL: The path to the file containing
                                the certificate revocation list (CRL) to use in verifying
                                a presented server certificate.'
                              type: string
                            clientCertificate:
                              description: REQUIRED if mode is `MUTUAL`.
                              type: string
                            credentialName:
                              description: The name of the secret that holds the TLS
                                certs for the client including the CA certificates.
                              type: string
                            insecureSkipVerify:
                              description: '`insecureSkipVerify` specifies whether
                                the proxy should skip verifying the CA signature and
                                SAN for the server certificate corresponding to the
                                host.'
                              nullable: true
                              type: boolean
                            mode:
                              description: |-
                                Indicates whether connections to this port should be secured using TLS.

                                Valid Options: DISABLE, SIMPLE, MUTUAL, ISTIO_MUTUAL
                              enum:
                              - DISABLE
                              - SIMPLE
                              - MUTUAL
                              - ISTIO_MUTUAL
                              type: string
                            privateKey:
                              description: REQUIRED if mode is `MUTUAL`.
                              type: string
                            sni:
                              description: SNI string to present to the server during
                                TLS handshake.
                              type: string
                            subjectAltNames:
                              description: A list of alternate names to verify the
                                subject identity in the certificate.
                              items:
                                type: string
                              type: array
                          type: object
                      type: object
                    maxItems: 4096
                    type: array
                  proxyProtocol:
                    description: The upstream PROXY protocol settings.
                    properties:
                      version:
                        description: |-
                          The PROXY protocol version to use.

                          Valid Options: V1, V2
                        enum:
                        - V1
                        - V2
                        type: string
                    type: object
                  tls:
                    description: TLS related settings for connections to the upstream
                      service.
                    properties:
                      caCertificates:
                        description: 'OPTIONAL: The path to the file containing certificate
                          authority certificates to use in verifying a presented server
                          certificate.'
                        type: string
                      caCrl:
                        description: 'OPTIONAL: The path to the file containing the
                          certificate revocation list (CRL) to use in verifying a
                          presented server certificate.'
                        type: string
                      clientCertificate:
                        description: REQUIRED if mode is `MUTUAL`.
                        type: string
                      credentialName:
                        description: The name of the secret that holds the TLS certs
                          for the client including the CA certificates.
                        type: string
                      insecureSkipVerify:
                        description: '`insecureSkipVerify` specifies whether the proxy
                          should skip verifying the CA signature and SAN for the server
                          certificate corresponding to the host.'
                        nullable: true
                        type: boolean
                      mode:
                        description: |-
                          Indicates whether connections to this port should be secured using TLS.

                          Valid Options: DISABLE, SIMPLE, MUTUAL, ISTIO_MUTUAL
                        enum:
                        - DISABLE
                        - SIMPLE
                        - MUTUAL
                        - ISTIO_MUTUAL
                        type: string
                      privateKey:
                        description: REQUIRED if mode is `MUTUAL`.
                        type: string
                      sni:
                        description: SNI string to present to the server during TLS
                          handshake.
                        type: string
                      subjectAltNames:
                        description: A list of alternate names to verify the subject
                          identity in the certificate.
                        items:
                          type: string
                        type: array
                    type: object
                  tunnel:
                    description: Configuration of tunneling TCP over other transport
                      or application layers for the host configured in the DestinationRule.
                    properties:
                      protocol:
                        description: Specifies which protocol to use for tunneling
                          the downstream connection.
                        type: string
                      targetHost:
                        description: Specifies a host to which the downstream connection
                          is tunneled.
                        type: string
                      targetPort:
                        description: Specifies a port to which the downstream connection
                          is tunneled.
                        maximum: 4294967295
                        minimum: 0
                        type: integer
                    required:
                    - targetHost
                    - targetPort
                    type: object
                type: object
              workloadSelector:
                description: Criteria used to select the specific set of pods/VMs
                  on which this `DestinationRule` configuration should be applied.
                properties:
                  matchLabels:
                    additionalProperties:
                      maxLength: 63
                      type: string
                      x-kubernetes-validations:
                      - message: wildcard not allowed in label value match
                        rule: '!self.contains(''*'')'
                    description: One or more labels that indicate a specific set of
                      pods/VMs on which a policy should be applied.
                    maxProperties: 4096
                    type: object
                    x-kubernetes-validations:
                    - message: wildcard not allowed in label key match
                      rule: self.all(key, !key.contains('*'))
                    - message: key must not be empty
                      rule: self.all(key, key.size() != 0)
                type: object
            required:
            - host
            type: object
          status:
            type: object
            x-kubernetes-preserve-unknown-fields: true
        type: object
    served: true
    storage: false
    subresources:
      status: {}
  - additionalPrinterColumns:
    - description: The name of a service from the service registry
      jsonPath: .spec.host
      name: Host
      type: string
    - description: 'CreationTimestamp is a timestamp representing the server time
        when this object was created. It is not guaranteed to be set in happens-before
        order across separate operations. Clients may not set this value. It is represented
        in RFC3339 form and is in UTC. Populated by the system. Read-only. Null for
        lists. More info: https://git.k8s.io/community/contributors/devel/api-conventions.md#metadata'
      jsonPath: .metadata.creationTimestamp
      name: Age
      type: date
    name: v1beta1
    schema:
      openAPIV3Schema:
        properties:
          spec:
            description: 'Configuration affecting load balancing, outlier detection,
              etc. See more details at: https://istio.io/docs/reference/config/networking/destination-rule.html'
            properties:
              exportTo:
                description: A list of namespaces to which this destination rule is
                  exported.
                items:
                  type: string
                type: array
              host:
                description: The name of a service from the service registry.
                type: string
              subsets:
                description: One or more named sets that represent individual versions
                  of a service.
                items:
                  properties:
                    labels:
                      additionalProperties:
                        type: string
                      description: Labels apply a filter over the endpoints of a service
                        in the service registry.
                      type: object
                    name:
                      description: Name of the subset.
                      type: string
                    trafficPolicy:
                      description: Traffic policies that apply to this subset.
                      properties:
                        connectionPool:
                          properties:
                            http:
                              description: HTTP connection pool settings.
                              properties:
                                h2UpgradePolicy:
                                  description: |-
                                    Specify if http1.1 connection should be upgraded to http2 for the associated destination.

                                    Valid Options: DEFAULT, DO_NOT_UPGRADE, UPGRADE
                                  enum:
                                  - DEFAULT
                                  - DO_NOT_UPGRADE
                                  - UPGRADE
                                  type: string
                                http1MaxPendingRequests:
                                  description: Maximum number of requests that will
                                    be queued while waiting for a ready connection
                                    pool connection.
                                  format: int32
                                  type: integer
                                http2MaxRequests:
                                  description: Maximum number of active requests to
                                    a destination.
                                  format: int32
                                  type: integer
                                idleTimeout:
                                  description: The idle timeout for upstream connection
                                    pool connections.
                                  type: string
                                  x-kubernetes-validations:
                                  - message: must be a valid duration greater than
                                      1ms
                                    rule: duration(self) >= duration('1ms')
                                maxConcurrentStreams:
                                  description: The maximum number of concurrent streams
                                    allowed for a peer on one HTTP/2 connection.
                                  format: int32
                                  type: integer
                                maxRequestsPerConnection:
                                  description: Maximum number of requests per connection
                                    to a backend.
                                  format: int32
                                  type: integer
                                maxRetries:
                                  description: Maximum number of retries that can
                                    be outstanding to all hosts in a cluster at a
                                    given time.
                                  format: int32
                                  type: integer
                                useClientProtocol:
                                  description: If set to true, client protocol will
                                    be preserved while initiating connection to backend.
                                  type: boolean
                              type: object
                            tcp:
                              description: Settings common to both HTTP and TCP upstream
                                connections.
                              properties:
                                connectTimeout:
                                  description: TCP connection timeout.
                                  type: string
                                  x-kubernetes-validations:
                                  - message: must be a valid duration greater than
                                      1ms
                                    rule: duration(self) >= duration('1ms')
                                idleTimeout:
                                  description: The idle timeout for TCP connections.
                                  type: string
                                  x-kubernetes-validations:
                                  - message: must be a valid duration greater than
                                      1ms
                                    rule: duration(self) >= duration('1ms')
                                maxConnectionDuration:
                                  description: The maximum duration of a connection.
                                  type: string
                                  x-kubernetes-validations:
                                  - message: must be a valid duration greater than
                                      1ms
                                    rule: duration(self) >= duration('1ms')
                                maxConnections:
                                  description: Maximum number of HTTP1 /TCP connections
                                    to a destination host.
                                  format: int32
                                  type: integer
                                tcpKeepalive:
                                  description: If set then set SO_KEEPALIVE on the
                                    socket to enable TCP Keepalives.
                                  properties:
                                    interval:
                                      description: The time duration between keep-alive
                                        probes.
                                      type: string
                                      x-kubernetes-validations:
                                      - message: must be a valid duration greater
                                          than 1ms
                                        rule: duration(self) >= duration('1ms')
                                    probes:
                                      description: Maximum number of keepalive probes
                                        to send without response before deciding the
                                        connection is dead.
                                      maximum: 4294967295
                                      minimum: 0
                                      type: integer
                                    time:
                                      description: The time duration a connection
                                        needs to be idle before keep-alive probes
                                        start being sent.
                                      type: string
                                      x-kubernetes-validations:
                                      - message: must be a valid duration greater
                                          than 1ms
                                        rule: duration(self) >= duration('1ms')
                                  type: object
                              type: object
                          type: object
                        loadBalancer:
                          description: Settings controlling the load balancer algorithms.
                          oneOf:
                          - not:
                              anyOf:
                              - required:
                                - simple
                              - required:
                                - consistentHash
                          - required:
                            - simple
                          - required:
                            - consistentHash
                          properties:
                            consistentHash:
                              allOf:
                              - oneOf:
                                - not:
                                    anyOf:
                                    - required:
                                      - httpHeaderName
                                    - required:
                                      - httpCookie
                                    - required:
                                      - useSourceIp
                                    - required:
                                      - httpQueryParameterName
                                - required:
                                  - httpHeaderName
                                - required:
                                  - httpCookie
                                - required:
                                  - useSourceIp
                                - required:
                                  - httpQueryParameterName
                              - oneOf:
                                - not:
                                    anyOf:
                                    - required:
                                      - ringHash
                                    - required:
                                      - maglev
                                - required:
                                  - ringHash
                                - required:
                                  - maglev
                              properties:
                                httpCookie:
                                  description: Hash based on HTTP cookie.
                                  properties:
                                    name:
                                      description: Name of the cookie.
                                      type: string
                                    path:
                                      description: Path to set for the cookie.
                                      type: string
                                    ttl:
                                      description: Lifetime of the cookie.
                                      type: string
                                  required:
                                  - name
                                  type: object
                                httpHeaderName:
                                  description: Hash based on a specific HTTP header.
                                  type: string
                                httpQueryParameterName:
                                  description: Hash based on a specific HTTP query
                                    parameter.
                                  type: string
                                maglev:
                                  description: The Maglev load balancer implements
                                    consistent hashing to backend hosts.
                                  properties:
                                    tableSize:
                                      description: The table size for Maglev hashing.
                                      minimum: 0
                                      type: integer
                                  type: object
                                minimumRingSize:
                                  description: Deprecated.
                                  minimum: 0
                                  type: integer
                                ringHash:
                                  description: The ring/modulo hash load balancer
                                    implements consistent hashing to backend hosts.
                                  properties:
                                    minimumRingSize:
                                      description: The minimum number of virtual nodes
                                        to use for the hash ring.
                                      minimum: 0
                                      type: integer
                                  type: object
                                useSourceIp:
                                  description: Hash based on the source IP address.
                                  type: boolean
                              type: object
                            localityLbSetting:
                              properties:
                                distribute:
                                  description: 'Optional: only one of distribute,
                                    failover or failoverPriority can be set.'
                                  items:
                                    properties:
                                      from:
                                        description: Originating locality, '/' separated,
                                          e.g.
                                        type: string
                                      to:
                                        additionalProperties:
                                          maximum: 4294967295
                                          minimum: 0
                                          type: integer
                                        description: Map of upstream localities to
                                          traffic distribution weights.
                                        type: object
                                    type: object
                                  type: array
                                enabled:
                                  description: enable locality load balancing, this
                                    is DestinationRule-level and will override mesh
                                    wide settings in entirety.
                                  nullable: true
                                  type: boolean
                                failover:
                                  description: 'Optional: only one of distribute,
                                    failover or failoverPriority can be set.'
                                  items:
                                    properties:
                                      from:
                                        description: Originating region.
                                        type: string
                                      to:
                                        description: Destination region the traffic
                                          will fail over to when endpoints in the
                                          'from' region becomes unhealthy.
                                        type: string
                                    type: object
                                  type: array
                                failoverPriority:
                                  description: failoverPriority is an ordered list
                                    of labels used to sort endpoints to do priority
                                    based load balancing.
                                  items:
                                    type: string
                                  type: array
                              type: object
                            simple:
                              description: |2-


                                Valid Options: LEAST_CONN, RANDOM, PASSTHROUGH, ROUND_ROBIN, LEAST_REQUEST
                              enum:
                              - UNSPECIFIED
                              - LEAST_CONN
                              - RANDOM
                              - PASSTHROUGH
                              - ROUND_ROBIN
                              - LEAST_REQUEST
                              type: string
                            warmupDurationSecs:
                              description: Represents the warmup duration of Service.
                              type: string
                              x-kubernetes-validations:
                              - message: must be a valid duration greater than 1ms
                                rule: duration(self) >= duration('1ms')
                          type: object
                        outlierDetection:
                          properties:
                            baseEjectionTime:
                              description: Minimum ejection duration.
                              type: string
                              x-kubernetes-validations:
                              - message: must be a valid duration greater than 1ms
                                rule: duration(self) >= duration('1ms')
                            consecutive5xxErrors:
                              description: Number of 5xx errors before a host is ejected
                                from the connection pool.
                              maximum: 4294967295
                              minimum: 0
                              nullable: true
                              type: integer
                            consecutiveErrors:
                              format: int32
                              type: integer
                            consecutiveGatewayErrors:
                              description: Number of gateway errors before a host
                                is ejected from the connection pool.
                              maximum: 4294967295
                              minimum: 0
                              nullable: true
                              type: integer
                            consecutiveLocalOriginFailures:
                              description: The number of consecutive locally originated
                                failures before ejection occurs.
                              maximum: 4294967295
                              minimum: 0
                              nullable: true
                              type: integer
                            interval:
                              description: Time interval between ejection sweep analysis.
                              type: string
                              x-kubernetes-validations:
                              - message: must be a valid duration greater than 1ms
                                rule: duration(self) >= duration('1ms')
                            maxEjectionPercent:
                              description: Maximum % of hosts in the load balancing
                                pool for the upstream service that can be ejected.
                              format: int32
                              type: integer
                            minHealthPercent:
                              description: Outlier detection will be enabled as long
                                as the associated load balancing pool has at least
                                min_health_percent hosts in healthy mode.
                              format: int32
                              type: integer
                            splitExternalLocalOriginErrors:
                              description: Determines whether to distinguish local
                                origin failures from external errors.
                              type: boolean
                          type: object
                        portLevelSettings:
                          description: Traffic policies specific to individual ports.
                          items:
                            properties:
                              connectionPool:
                                properties:
                                  http:
                                    description: HTTP connection pool settings.
                                    properties:
                                      h2UpgradePolicy:
                                        description: |-
                                          Specify if http1.1 connection should be upgraded to http2 for the associated destination.

                                          Valid Options: DEFAULT, DO_NOT_UPGRADE, UPGRADE
                                        enum:
                                        - DEFAULT
                                        - DO_NOT_UPGRADE
                                        - UPGRADE
                                        type: string
                                      http1MaxPendingRequests:
                                        description: Maximum number of requests that
                                          will be queued while waiting for a ready
                                          connection pool connection.
                                        format: int32
                                        type: integer
                                      http2MaxRequests:
                                        description: Maximum number of active requests
                                          to a destination.
                                        format: int32
                                        type: integer
                                      idleTimeout:
                                        description: The idle timeout for upstream
                                          connection pool connections.
                                        type: string
                                        x-kubernetes-validations:
                                        - message: must be a valid duration greater
                                            than 1ms
                                          rule: duration(self) >= duration('1ms')
                                      maxConcurrentStreams:
                                        description: The maximum number of concurrent
                                          streams allowed for a peer on one HTTP/2
                                          connection.
                                        format: int32
                                        type: integer
                                      maxRequestsPerConnection:
                                        description: Maximum number of requests per
                                          connection to a backend.
                                        format: int32
                                        type: integer
                                      maxRetries:
                                        description: Maximum number of retries that
                                          can be outstanding to all hosts in a cluster
                                          at a given time.
                                        format: int32
                                        type: integer
                                      useClientProtocol:
                                        description: If set to true, client protocol
                                          will be preserved while initiating connection
                                          to backend.
                                        type: boolean
                                    type: object
                                  tcp:
                                    description: Settings common to both HTTP and
                                      TCP upstream connections.
                                    properties:
                                      connectTimeout:
                                        description: TCP connection timeout.
                                        type: string
                                        x-kubernetes-validations:
                                        - message: must be a valid duration greater
                                            than 1ms
                                          rule: duration(self) >= duration('1ms')
                                      idleTimeout:
                                        description: The idle timeout for TCP connections.
                                        type: string
                                        x-kubernetes-validations:
                                        - message: must be a valid duration greater
                                            than 1ms
                                          rule: duration(self) >= duration('1ms')
                                      maxConnectionDuration:
                                        description: The maximum duration of a connection.
                                        type: string
                                        x-kubernetes-validations:
                                        - message: must be a valid duration greater
                                            than 1ms
                                          rule: duration(self) >= duration('1ms')
                                      maxConnections:
                                        description: Maximum number of HTTP1 /TCP
                                          connections to a destination host.
                                        format: int32
                                        type: integer
                                      tcpKeepalive:
                                        description: If set then set SO_KEEPALIVE
                                          on the socket to enable TCP Keepalives.
                                        properties:
                                          interval:
                                            description: The time duration between
                                              keep-alive probes.
                                            type: string
                                            x-kubernetes-validations:
                                            - message: must be a valid duration greater
                                                than 1ms
                                              rule: duration(self) >= duration('1ms')
                                          probes:
                                            description: Maximum number of keepalive
                                              probes to send without response before
                                              deciding the connection is dead.
                                            maximum: 4294967295
                                            minimum: 0
                                            type: integer
                                          time:
                                            description: The time duration a connection
                                              needs to be idle before keep-alive probes
                                              start being sent.
                                            type: string
                                            x-kubernetes-validations:
                                            - message: must be a valid duration greater
                                                than 1ms
                                              rule: duration(self) >= duration('1ms')
                                        type: object
                                    type: object
                                type: object
                              loadBalancer:
                                description: Settings controlling the load balancer
                                  algorithms.
                                oneOf:
                                - not:
                                    anyOf:
                                    - required:
                                      - simple
                                    - required:
                                      - consistentHash
                                - required:
                                  - simple
                                - required:
                                  - consistentHash
                                properties:
                                  consistentHash:
                                    allOf:
                                    - oneOf:
                                      - not:
                                          anyOf:
                                          - required:
                                            - httpHeaderName
                                          - required:
                                            - httpCookie
                                          - required:
                                            - useSourceIp
                                          - required:
                                            - httpQueryParameterName
                                      - required:
                                        - httpHeaderName
                                      - required:
                                        - httpCookie
                                      - required:
                                        - useSourceIp
                                      - required:
                                        - httpQueryParameterName
                                    - oneOf:
                                      - not:
                                          anyOf:
                                          - required:
                                            - ringHash
                                          - required:
                                            - maglev
                                      - required:
                                        - ringHash
                                      - required:
                                        - maglev
                                    properties:
                                      httpCookie:
                                        description: Hash based on HTTP cookie.
                                        properties:
                                          name:
                                            description: Name of the cookie.
                                            type: string
                                          path:
                                            description: Path to set for the cookie.
                                            type: string
                                          ttl:
                                            description: Lifetime of the cookie.
                                            type: string
                                        required:
                                        - name
                                        type: object
                                      httpHeaderName:
                                        description: Hash based on a specific HTTP
                                          header.
                                        type: string
                                      httpQueryParameterName:
                                        description: Hash based on a specific HTTP
                                          query parameter.
                                        type: string
                                      maglev:
                                        description: The Maglev load balancer implements
                                          consistent hashing to backend hosts.
                                        properties:
                                          tableSize:
                                            description: The table size for Maglev
                                              hashing.
                                            minimum: 0
                                            type: integer
                                        type: object
                                      minimumRingSize:
                                        description: Deprecated.
                                        minimum: 0
                                        type: integer
                                      ringHash:
                                        description: The ring/modulo hash load balancer
                                          implements consistent hashing to backend
                                          hosts.
                                        properties:
                                          minimumRingSize:
                                            description: The minimum number of virtual
                                              nodes to use for the hash ring.
                                            minimum: 0
                                            type: integer
                                        type: object
                                      useSourceIp:
                                        description: Hash based on the source IP address.
                                        type: boolean
                                    type: object
                                  localityLbSetting:
                                    properties:
                                      distribute:
                                        description: 'Optional: only one of distribute,
                                          failover or failoverPriority can be set.'
                                        items:
                                          properties:
                                            from:
                                              description: Originating locality, '/'
                                                separated, e.g.
                                              type: string
                                            to:
                                              additionalProperties:
                                                maximum: 4294967295
                                                minimum: 0
                                                type: integer
                                              description: Map of upstream localities
                                                to traffic distribution weights.
                                              type: object
                                          type: object
                                        type: array
                                      enabled:
                                        description: enable locality load balancing,
                                          this is DestinationRule-level and will override
                                          mesh wide settings in entirety.
                                        nullable: true
                                        type: boolean
                                      failover:
                                        description: 'Optional: only one of distribute,
                                          failover or failoverPriority can be set.'
                                        items:
                                          properties:
                                            from:
                                              description: Originating region.
                                              type: string
                                            to:
                                              description: Destination region the
                                                traffic will fail over to when endpoints
                                                in the 'from' region becomes unhealthy.
                                              type: string
                                          type: object
                                        type: array
                                      failoverPriority:
                                        description: failoverPriority is an ordered
                                          list of labels used to sort endpoints to
                                          do priority based load balancing.
                                        items:
                                          type: string
                                        type: array
                                    type: object
                                  simple:
                                    description: |2-


                                      Valid Options: LEAST_CONN, RANDOM, PASSTHROUGH, ROUND_ROBIN, LEAST_REQUEST
                                    enum:
                                    - UNSPECIFIED
                                    - LEAST_CONN
                                    - RANDOM
                                    - PASSTHROUGH
                                    - ROUND_ROBIN
                                    - LEAST_REQUEST
                                    type: string
                                  warmupDurationSecs:
                                    description: Represents the warmup duration of
                                      Service.
                                    type: string
                                    x-kubernetes-validations:
                                    - message: must be a valid duration greater than
                                        1ms
                                      rule: duration(self) >= duration('1ms')
                                type: object
                              outlierDetection:
                                properties:
                                  baseEjectionTime:
                                    description: Minimum ejection duration.
                                    type: string
                                    x-kubernetes-validations:
                                    - message: must be a valid duration greater than
                                        1ms
                                      rule: duration(self) >= duration('1ms')
                                  consecutive5xxErrors:
                                    description: Number of 5xx errors before a host
                                      is ejected from the connection pool.
                                    maximum: 4294967295
                                    minimum: 0
                                    nullable: true
                                    type: integer
                                  consecutiveErrors:
                                    format: int32
                                    type: integer
                                  consecutiveGatewayErrors:
                                    description: Number of gateway errors before a
                                      host is ejected from the connection pool.
                                    maximum: 4294967295
                                    minimum: 0
                                    nullable: true
                                    type: integer
                                  consecutiveLocalOriginFailures:
                                    description: The number of consecutive locally
                                      originated failures before ejection occurs.
                                    maximum: 4294967295
                                    minimum: 0
                                    nullable: true
                                    type: integer
                                  interval:
                                    description: Time interval between ejection sweep
                                      analysis.
                                    type: string
                                    x-kubernetes-validations:
                                    - message: must be a valid duration greater than
                                        1ms
                                      rule: duration(self) >= duration('1ms')
                                  maxEjectionPercent:
                                    description: Maximum % of hosts in the load balancing
                                      pool for the upstream service that can be ejected.
                                    format: int32
                                    type: integer
                                  minHealthPercent:
                                    description: Outlier detection will be enabled
                                      as long as the associated load balancing pool
                                      has at least min_health_percent hosts in healthy
                                      mode.
                                    format: int32
                                    type: integer
                                  splitExternalLocalOriginErrors:
                                    description: Determines whether to distinguish
                                      local origin failures from external errors.
                                    type: boolean
                                type: object
                              port:
                                description: Specifies the number of a port on the
                                  destination service on which this policy is being
                                  applied.
                                properties:
                                  number:
                                    maximum: 4294967295
                                    minimum: 0
                                    type: integer
                                type: object
                              tls:
                                description: TLS related settings for connections
                                  to the upstream service.
                                properties:
                                  caCertificates:
                                    description: 'OPTIONAL: The path to the file containing
                                      certificate authority certificates to use in
                                      verifying a presented server certificate.'
                                    type: string
                                  caCrl:
                                    description: 'OPTIONAL: The path to the file containing
                                      the certificate revocation list (CRL) to use
                                      in verifying a presented server certificate.'
                                    type: string
                                  clientCertificate:
                                    description: REQUIRED if mode is `MUTUAL`.
                                    type: string
                                  credentialName:
                                    description: The name of the secret that holds
                                      the TLS certs for the client including the CA
                                      certificates.
                                    type: string
                                  insecureSkipVerify:
                                    description: '`insecureSkipVerify` specifies whether
                                      the proxy should skip verifying the CA signature
                                      and SAN for the server certificate corresponding
                                      to the host.'
                                    nullable: true
                                    type: boolean
                                  mode:
                                    description: |-
                                      Indicates whether connections to this port should be secured using TLS.

                                      Valid Options: DISABLE, SIMPLE, MUTUAL, ISTIO_MUTUAL
                                    enum:
                                    - DISABLE
                                    - SIMPLE
                                    - MUTUAL
                                    - ISTIO_MUTUAL
                                    type: string
                                  privateKey:
                                    description: REQUIRED if mode is `MUTUAL`.
                                    type: string
                                  sni:
                                    description: SNI string to present to the server
                                      during TLS handshake.
                                    type: string
                                  subjectAltNames:
                                    description: A list of alternate names to verify
                                      the subject identity in the certificate.
                                    items:
                                      type: string
                                    type: array
                                type: object
                            type: object
                          maxItems: 4096
                          type: array
                        proxyProtocol:
                          description: The upstream PROXY protocol settings.
                          properties:
                            version:
                              description: |-
                                The PROXY protocol version to use.

                                Valid Options: V1, V2
                              enum:
                              - V1
                              - V2
                              type: string
                          type: object
                        tls:
                          description: TLS related settings for connections to the
                            upstream service.
                          properties:
                            caCertificates:
                              description: 'OPTIONAL: The path to the file containing
                                certificate authority certificates to use in verifying
                                a presented server certificate.'
                              type: string
                            caCrl:
                              description: 'OPTIONAL: The path to the file containing
                                the certificate revocation list (CRL) to use in verifying
                                a presented server certificate.'
                              type: string
                            clientCertificate:
                              description: REQUIRED if mode is `MUTUAL`.
                              type: string
                            credentialName:
                              description: The name of the secret that holds the TLS
                                certs for the client including the CA certificates.
                              type: string
                            insecureSkipVerify:
                              description: '`insecureSkipVerify` specifies whether
                                the proxy should skip verifying the CA signature and
                                SAN for the server certificate corresponding to the
                                host.'
                              nullable: true
                              type: boolean
                            mode:
                              description: |-
                                Indicates whether connections to this port should be secured using TLS.

                                Valid Options: DISABLE, SIMPLE, MUTUAL, ISTIO_MUTUAL
                              enum:
                              - DISABLE
                              - SIMPLE
                              - MUTUAL
                              - ISTIO_MUTUAL
                              type: string
                            privateKey:
                              description: REQUIRED if mode is `MUTUAL`.
                              type: string
                            sni:
                              description: SNI string to present to the server during
                                TLS handshake.
                              type: string
                            subjectAltNames:
                              description: A list of alternate names to verify the
                                subject identity in the certificate.
                              items:
                                type: string
                              type: array
                          type: object
                        tunnel:
                          description: Configuration of tunneling TCP over other transport
                            or application layers for the host configured in the DestinationRule.
                          properties:
                            protocol:
                              description: Specifies which protocol to use for tunneling
                                the downstream connection.
                              type: string
                            targetHost:
                              description: Specifies a host to which the downstream
                                connection is tunneled.
                              type: string
                            targetPort:
                              description: Specifies a port to which the downstream
                                connection is tunneled.
                              maximum: 4294967295
                              minimum: 0
                              type: integer
                          required:
                          - targetHost
                          - targetPort
                          type: object
                      type: object
                  required:
                  - name
                  type: object
                type: array
              trafficPolicy:
                description: Traffic policies to apply (load balancing policy, connection
                  pool sizes, outlier detection).
                properties:
                  connectionPool:
                    properties:
                      http:
                        description: HTTP connection pool settings.
                        properties:
                          h2UpgradePolicy:
                            description: |-
                              Specify if http1.1 connection should be upgraded to http2 for the associated destination.

                              Valid Options: DEFAULT, DO_NOT_UPGRADE, UPGRADE
                            enum:
                            - DEFAULT
                            - DO_NOT_UPGRADE
                            - UPGRADE
                            type: string
                          http1MaxPendingRequests:
                            description: Maximum number of requests that will be queued
                              while waiting for a ready connection pool connection.
                            format: int32
                            type: integer
                          http2MaxRequests:
                            description: Maximum number of active requests to a destination.
                            format: int32
                            type: integer
                          idleTimeout:
                            description: The idle timeout for upstream connection
                              pool connections.
                            type: string
                            x-kubernetes-validations:
                            - message: must be a valid duration greater than 1ms
                              rule: duration(self) >= duration('1ms')
                          maxConcurrentStreams:
                            description: The maximum number of concurrent streams
                              allowed for a peer on one HTTP/2 connection.
                            format: int32
                            type: integer
                          maxRequestsPerConnection:
                            description: Maximum number of requests per connection
                              to a backend.
                            format: int32
                            type: integer
                          maxRetries:
                            description: Maximum number of retries that can be outstanding
                              to all hosts in a cluster at a given time.
                            format: int32
                            type: integer
                          useClientProtocol:
                            description: If set to true, client protocol will be preserved
                              while initiating connection to backend.
                            type: boolean
                        type: object
                      tcp:
                        description: Settings common to both HTTP and TCP upstream
                          connections.
                        properties:
                          connectTimeout:
                            description: TCP connection timeout.
                            type: string
                            x-kubernetes-validations:
                            - message: must be a valid duration greater than 1ms
                              rule: duration(self) >= duration('1ms')
                          idleTimeout:
                            description: The idle timeout for TCP connections.
                            type: string
                            x-kubernetes-validations:
                            - message: must be a valid duration greater than 1ms
                              rule: duration(self) >= duration('1ms')
                          maxConnectionDuration:
                            description: The maximum duration of a connection.
                            type: string
                            x-kubernetes-validations:
                            - message: must be a valid duration greater than 1ms
                              rule: duration(self) >= duration('1ms')
                          maxConnections:
                            description: Maximum number of HTTP1 /TCP connections
                              to a destination host.
                            format: int32
                            type: integer
                          tcpKeepalive:
                            description: If set then set SO_KEEPALIVE on the socket
                              to enable TCP Keepalives.
                            properties:
                              interval:
                                description: The time duration between keep-alive
                                  probes.
                                type: string
                                x-kubernetes-validations:
                                - message: must be a valid duration greater than 1ms
                                  rule: duration(self) >= duration('1ms')
                              probes:
                                description: Maximum number of keepalive probes to
                                  send without response before deciding the connection
                                  is dead.
                                maximum: 4294967295
                                minimum: 0
                                type: integer
                              time:
                                description: The time duration a connection needs
                                  to be idle before keep-alive probes start being
                                  sent.
                                type: string
                                x-kubernetes-validations:
                                - message: must be a valid duration greater than 1ms
                                  rule: duration(self) >= duration('1ms')
                            type: object
                        type: object
                    type: object
                  loadBalancer:
                    description: Settings controlling the load balancer algorithms.
                    oneOf:
                    - not:
                        anyOf:
                        - required:
                          - simple
                        - required:
                          - consistentHash
                    - required:
                      - simple
                    - required:
                      - consistentHash
                    properties:
                      consistentHash:
                        allOf:
                        - oneOf:
                          - not:
                              anyOf:
                              - required:
                                - httpHeaderName
                              - required:
                                - httpCookie
                              - required:
                                - useSourceIp
                              - required:
                                - httpQueryParameterName
                          - required:
                            - httpHeaderName
                          - required:
                            - httpCookie
                          - required:
                            - useSourceIp
                          - required:
                            - httpQueryParameterName
                        - oneOf:
                          - not:
                              anyOf:
                              - required:
                                - ringHash
                              - required:
                                - maglev
                          - required:
                            - ringHash
                          - required:
                            - maglev
                        properties:
                          httpCookie:
                            description: Hash based on HTTP cookie.
                            properties:
                              name:
                                description: Name of the cookie.
                                type: string
                              path:
                                description: Path to set for the cookie.
                                type: string
                              ttl:
                                description: Lifetime of the cookie.
                                type: string
                            required:
                            - name
                            type: object
                          httpHeaderName:
                            description: Hash based on a specific HTTP header.
                            type: string
                          httpQueryParameterName:
                            description: Hash based on a specific HTTP query parameter.
                            type: string
                          maglev:
                            description: The Maglev load balancer implements consistent
                              hashing to backend hosts.
                            properties:
                              tableSize:
                                description: The table size for Maglev hashing.
                                minimum: 0
                                type: integer
                            type: object
                          minimumRingSize:
                            description: Deprecated.
                            minimum: 0
                            type: integer
                          ringHash:
                            description: The ring/modulo hash load balancer implements
                              consistent hashing to backend hosts.
                            properties:
                              minimumRingSize:
                                description: The minimum number of virtual nodes to
                                  use for the hash ring.
                                minimum: 0
                                type: integer
                            type: object
                          useSourceIp:
                            description: Hash based on the source IP address.
                            type: boolean
                        type: object
                      localityLbSetting:
                        properties:
                          distribute:
                            description: 'Optional: only one of distribute, failover
                              or failoverPriority can be set.'
                            items:
                              properties:
                                from:
                                  description: Originating locality, '/' separated,
                                    e.g.
                                  type: string
                                to:
                                  additionalProperties:
                                    maximum: 4294967295
                                    minimum: 0
                                    type: integer
                                  description: Map of upstream localities to traffic
                                    distribution weights.
                                  type: object
                              type: object
                            type: array
                          enabled:
                            description: enable locality load balancing, this is DestinationRule-level
                              and will override mesh wide settings in entirety.
                            nullable: true
                            type: boolean
                          failover:
                            description: 'Optional: only one of distribute, failover
                              or failoverPriority can be set.'
                            items:
                              properties:
                                from:
                                  description: Originating region.
                                  type: string
                                to:
                                  description: Destination region the traffic will
                                    fail over to when endpoints in the 'from' region
                                    becomes unhealthy.
                                  type: string
                              type: object
                            type: array
                          failoverPriority:
                            description: failoverPriority is an ordered list of labels
                              used to sort endpoints to do priority based load balancing.
                            items:
                              type: string
                            type: array
                        type: object
                      simple:
                        description: |2-


                          Valid Options: LEAST_CONN, RANDOM, PASSTHROUGH, ROUND_ROBIN, LEAST_REQUEST
                        enum:
                        - UNSPECIFIED
                        - LEAST_CONN
                        - RANDOM
                        - PASSTHROUGH
                        - ROUND_ROBIN
                        - LEAST_REQUEST
                        type: string
                      warmupDurationSecs:
                        description: Represents the warmup duration of Service.
                        type: string
                        x-kubernetes-validations:
                        - message: must be a valid duration greater than 1ms
                          rule: duration(self) >= duration('1ms')
                    type: object
                  outlierDetection:
                    properties:
                      baseEjectionTime:
                        description: Minimum ejection duration.
                        type: string
                        x-kubernetes-validations:
                        - message: must be a valid duration greater than 1ms
                          rule: duration(self) >= duration('1ms')
                      consecutive5xxErrors:
                        description: Number of 5xx errors before a host is ejected
                          from the connection pool.
                        maximum: 4294967295
                        minimum: 0
                        nullable: true
                        type: integer
                      consecutiveErrors:
                        format: int32
                        type: integer
                      consecutiveGatewayErrors:
                        description: Number of gateway errors before a host is ejected
                          from the connection pool.
                        maximum: 4294967295
                        minimum: 0
                        nullable: true
                        type: integer
                      consecutiveLocalOriginFailures:
                        description: The number of consecutive locally originated
                          failures before ejection occurs.
                        maximum: 4294967295
                        minimum: 0
                        nullable: true
                        type: integer
                      interval:
                        description: Time interval between ejection sweep analysis.
                        type: string
                        x-kubernetes-validations:
                        - message: must be a valid duration greater than 1ms
                          rule: duration(self) >= duration('1ms')
                      maxEjectionPercent:
                        description: Maximum % of hosts in the load balancing pool
                          for the upstream service that can be ejected.
                        format: int32
                        type: integer
                      minHealthPercent:
                        description: Outlier detection will be enabled as long as
                          the associated load balancing pool has at least min_health_percent
                          hosts in healthy mode.
                        format: int32
                        type: integer
                      splitExternalLocalOriginErrors:
                        description: Determines whether to distinguish local origin
                          failures from external errors.
                        type: boolean
                    type: object
                  portLevelSettings:
                    description: Traffic policies specific to individual ports.
                    items:
                      properties:
                        connectionPool:
                          properties:
                            http:
                              description: HTTP connection pool settings.
                              properties:
                                h2UpgradePolicy:
                                  description: |-
                                    Specify if http1.1 connection should be upgraded to http2 for the associated destination.

                                    Valid Options: DEFAULT, DO_NOT_UPGRADE, UPGRADE
                                  enum:
                                  - DEFAULT
                                  - DO_NOT_UPGRADE
                                  - UPGRADE
                                  type: string
                                http1MaxPendingRequests:
                                  description: Maximum number of requests that will
                                    be queued while waiting for a ready connection
                                    pool connection.
                                  format: int32
                                  type: integer
                                http2MaxRequests:
                                  description: Maximum number of active requests to
                                    a destination.
                                  format: int32
                                  type: integer
                                idleTimeout:
                                  description: The idle timeout for upstream connection
                                    pool connections.
                                  type: string
                                  x-kubernetes-validations:
                                  - message: must be a valid duration greater than
                                      1ms
                                    rule: duration(self) >= duration('1ms')
                                maxConcurrentStreams:
                                  description: The maximum number of concurrent streams
                                    allowed for a peer on one HTTP/2 connection.
                                  format: int32
                                  type: integer
                                maxRequestsPerConnection:
                                  description: Maximum number of requests per connection
                                    to a backend.
                                  format: int32
                                  type: integer
                                maxRetries:
                                  description: Maximum number of retries that can
                                    be outstanding to all hosts in a cluster at a
                                    given time.
                                  format: int32
                                  type: integer
                                useClientProtocol:
                                  description: If set to true, client protocol will
                                    be preserved while initiating connection to backend.
                                  type: boolean
                              type: object
                            tcp:
                              description: Settings common to both HTTP and TCP upstream
                                connections.
                              properties:
                                connectTimeout:
                                  description: TCP connection timeout.
                                  type: string
                                  x-kubernetes-validations:
                                  - message: must be a valid duration greater than
                                      1ms
                                    rule: duration(self) >= duration('1ms')
                                idleTimeout:
                                  description: The idle timeout for TCP connections.
                                  type: string
                                  x-kubernetes-validations:
                                  - message: must be a valid duration greater than
                                      1ms
                                    rule: duration(self) >= duration('1ms')
                                maxConnectionDuration:
                                  description: The maximum duration of a connection.
                                  type: string
                                  x-kubernetes-validations:
                                  - message: must be a valid duration greater than
                                      1ms
                                    rule: duration(self) >= duration('1ms')
                                maxConnections:
                                  description: Maximum number of HTTP1 /TCP connections
                                    to a destination host.
                                  format: int32
                                  type: integer
                                tcpKeepalive:
                                  description: If set then set SO_KEEPALIVE on the
                                    socket to enable TCP Keepalives.
                                  properties:
                                    interval:
                                      description: The time duration between keep-alive
                                        probes.
                                      type: string
                                      x-kubernetes-validations:
                                      - message: must be a valid duration greater
                                          than 1ms
                                        rule: duration(self) >= duration('1ms')
                                    probes:
                                      description: Maximum number of keepalive probes
                                        to send without response before deciding the
                                        connection is dead.
                                      maximum: 4294967295
                                      minimum: 0
                                      type: integer
                                    time:
                                      description: The time duration a connection
                                        needs to be idle before keep-alive probes
                                        start being sent.
                                      type: string
                                      x-kubernetes-validations:
                                      - message: must be a valid duration greater
                                          than 1ms
                                        rule: duration(self) >= duration('1ms')
                                  type: object
                              type: object
                          type: object
                        loadBalancer:
                          description: Settings controlling the load balancer algorithms.
                          oneOf:
                          - not:
                              anyOf:
                              - required:
                                - simple
                              - required:
                                - consistentHash
                          - required:
                            - simple
                          - required:
                            - consistentHash
                          properties:
                            consistentHash:
                              allOf:
                              - oneOf:
                                - not:
                                    anyOf:
                                    - required:
                                      - httpHeaderName
                                    - required:
                                      - httpCookie
                                    - required:
                                      - useSourceIp
                                    - required:
                                      - httpQueryParameterName
                                - required:
                                  - httpHeaderName
                                - required:
                                  - httpCookie
                                - required:
                                  - useSourceIp
                                - required:
                                  - httpQueryParameterName
                              - oneOf:
                                - not:
                                    anyOf:
                                    - required:
                                      - ringHash
                                    - required:
                                      - maglev
                                - required:
                                  - ringHash
                                - required:
                                  - maglev
                              properties:
                                httpCookie:
                                  description: Hash based on HTTP cookie.
                                  properties:
                                    name:
                                      description: Name of the cookie.
                                      type: string
                                    path:
                                      description: Path to set for the cookie.
                                      type: string
                                    ttl:
                                      description: Lifetime of the cookie.
                                      type: string
                                  required:
                                  - name
                                  type: object
                                httpHeaderName:
                                  description: Hash based on a specific HTTP header.
                                  type: string
                                httpQueryParameterName:
                                  description: Hash based on a specific HTTP query
                                    parameter.
                                  type: string
                                maglev:
                                  description: The Maglev load balancer implements
                                    consistent hashing to backend hosts.
                                  properties:
                                    tableSize:
                                      description: The table size for Maglev hashing.
                                      minimum: 0
                                      type: integer
                                  type: object
                                minimumRingSize:
                                  description: Deprecated.
                                  minimum: 0
                                  type: integer
                                ringHash:
                                  description: The ring/modulo hash load balancer
                                    implements consistent hashing to backend hosts.
                                  properties:
                                    minimumRingSize:
                                      description: The minimum number of virtual nodes
                                        to use for the hash ring.
                                      minimum: 0
                                      type: integer
                                  type: object
                                useSourceIp:
                                  description: Hash based on the source IP address.
                                  type: boolean
                              type: object
                            localityLbSetting:
                              properties:
                                distribute:
                                  description: 'Optional: only one of distribute,
                                    failover or failoverPriority can be set.'
                                  items:
                                    properties:
                                      from:
                                        description: Originating locality, '/' separated,
                                          e.g.
                                        type: string
                                      to:
                                        additionalProperties:
                                          maximum: 4294967295
                                          minimum: 0
                                          type: integer
                                        description: Map of upstream localities to
                                          traffic distribution weights.
                                        type: object
                                    type: object
                                  type: array
                                enabled:
                                  description: enable locality load balancing, this
                                    is DestinationRule-level and will override mesh
                                    wide settings in entirety.
                                  nullable: true
                                  type: boolean
                                failover:
                                  description: 'Optional: only one of distribute,
                                    failover or failoverPriority can be set.'
                                  items:
                                    properties:
                                      from:
                                        description: Originating region.
                                        type: string
                                      to:
                                        description: Destination region the traffic
                                          will fail over to when endpoints in the
                                          'from' region becomes unhealthy.
                                        type: string
                                    type: object
                                  type: array
                                failoverPriority:
                                  description: failoverPriority is an ordered list
                                    of labels used to sort endpoints to do priority
                                    based load balancing.
                                  items:
                                    type: string
                                  type: array
                              type: object
                            simple:
                              description: |2-


                                Valid Options: LEAST_CONN, RANDOM, PASSTHROUGH, ROUND_ROBIN, LEAST_REQUEST
                              enum:
                              - UNSPECIFIED
                              - LEAST_CONN
                              - RANDOM
                              - PASSTHROUGH
                              - ROUND_ROBIN
                              - LEAST_REQUEST
                              type: string
                            warmupDurationSecs:
                              description: Represents the warmup duration of Service.
                              type: string
                              x-kubernetes-validations:
                              - message: must be a valid duration greater than 1ms
                                rule: duration(self) >= duration('1ms')
                          type: object
                        outlierDetection:
                          properties:
                            baseEjectionTime:
                              description: Minimum ejection duration.
                              type: string
                              x-kubernetes-validations:
                              - message: must be a valid duration greater than 1ms
                                rule: duration(self) >= duration('1ms')
                            consecutive5xxErrors:
                              description: Number of 5xx errors before a host is ejected
                                from the connection pool.
                              maximum: 4294967295
                              minimum: 0
                              nullable: true
                              type: integer
                            consecutiveErrors:
                              format: int32
                              type: integer
                            consecutiveGatewayErrors:
                              description: Number of gateway errors before a host
                                is ejected from the connection pool.
                              maximum: 4294967295
                              minimum: 0
                              nullable: true
                              type: integer
                            consecutiveLocalOriginFailures:
                              description: The number of consecutive locally originated
                                failures before ejection occurs.
                              maximum: 4294967295
                              minimum: 0
                              nullable: true
                              type: integer
                            interval:
                              description: Time interval between ejection sweep analysis.
                              type: string
                              x-kubernetes-validations:
                              - message: must be a valid duration greater than 1ms
                                rule: duration(self) >= duration('1ms')
                            maxEjectionPercent:
                              description: Maximum % of hosts in the load balancing
                                pool for the upstream service that can be ejected.
                              format: int32
                              type: integer
                            minHealthPercent:
                              description: Outlier detection will be enabled as long
                                as the associated load balancing pool has at least
                                min_health_percent hosts in healthy mode.
                              format: int32
                              type: integer
                            splitExternalLocalOriginErrors:
                              description: Determines whether to distinguish local
                                origin failures from external errors.
                              type: boolean
                          type: object
                        port:
                          description: Specifies the number of a port on the destination
                            service on which this policy is being applied.
                          properties:
                            number:
                              maximum: 4294967295
                              minimum: 0
                              type: integer
                          type: object
                        tls:
                          description: TLS related settings for connections to the
                            upstream service.
                          properties:
                            caCertificates:
                              description: 'OPTIONAL: The path to the file containing
                                certificate authority certificates to use in verifying
                                a presented server certificate.'
                              type: string
                            caCrl:
                              description: 'OPTIONAL: The path to the file containing
                                the certificate revocation list (CRL) to use in verifying
                                a presented server certificate.'
                              type: string
                            clientCertificate:
                              description: REQUIRED if mode is `MUTUAL`.
                              type: string
                            credentialName:
                              description: The name of the secret that holds the TLS
                                certs for the client including the CA certificates.
                              type: string
                            insecureSkipVerify:
                              description: '`insecureSkipVerify` specifies whether
                                the proxy should skip verifying the CA signature and
                                SAN for the server certificate corresponding to the
                                host.'
                              nullable: true
                              type: boolean
                            mode:
                              description: |-
                                Indicates whether connections to this port should be secured using TLS.

                                Valid Options: DISABLE, SIMPLE, MUTUAL, ISTIO_MUTUAL
                              enum:
                              - DISABLE
                              - SIMPLE
                              - MUTUAL
                              - ISTIO_MUTUAL
                              type: string
                            privateKey:
                              description: REQUIRED if mode is `MUTUAL`.
                              type: string
                            sni:
                              description: SNI string to present to the server during
                                TLS handshake.
                              type: string
                            subjectAltNames:
                              description: A list of alternate names to verify the
                                subject identity in the certificate.
                              items:
                                type: string
                              type: array
                          type: object
                      type: object
                    maxItems: 4096
                    type: array
                  proxyProtocol:
                    description: The upstream PROXY protocol settings.
                    properties:
                      version:
                        description: |-
                          The PROXY protocol version to use.

                          Valid Options: V1, V2
                        enum:
                        - V1
                        - V2
                        type: string
                    type: object
                  tls:
                    description: TLS related settings for connections to the upstream
                      service.
                    properties:
                      caCertificates:
                        description: 'OPTIONAL: The path to the file containing certificate
                          authority certificates to use in verifying a presented server
                          certificate.'
                        type: string
                      caCrl:
                        description: 'OPTIONAL: The path to the file containing the
                          certificate revocation list (CRL) to use in verifying a
                          presented server certificate.'
                        type: string
                      clientCertificate:
                        description: REQUIRED if mode is `MUTUAL`.
                        type: string
                      credentialName:
                        description: The name of the secret that holds the TLS certs
                          for the client including the CA certificates.
                        type: string
                      insecureSkipVerify:
                        description: '`insecureSkipVerify` specifies whether the proxy
                          should skip verifying the CA signature and SAN for the server
                          certificate corresponding to the host.'
                        nullable: true
                        type: boolean
                      mode:
                        description: |-
                          Indicates whether connections to this port should be secured using TLS.

                          Valid Options: DISABLE, SIMPLE, MUTUAL, ISTIO_MUTUAL
                        enum:
                        - DISABLE
                        - SIMPLE
                        - MUTUAL
                        - ISTIO_MUTUAL
                        type: string
                      privateKey:
                        description: REQUIRED if mode is `MUTUAL`.
                        type: string
                      sni:
                        description: SNI string to present to the server during TLS
                          handshake.
                        type: string
                      subjectAltNames:
                        description: A list of alternate names to verify the subject
                          identity in the certificate.
                        items:
                          type: string
                        type: array
                    type: object
                  tunnel:
                    description: Configuration of tunneling TCP over other transport
                      or application layers for the host configured in the DestinationRule.
                    properties:
                      protocol:
                        description: Specifies which protocol to use for tunneling
                          the downstream connection.
                        type: string
                      targetHost:
                        description: Specifies a host to which the downstream connection
                          is tunneled.
                        type: string
                      targetPort:
                        description: Specifies a port to which the downstream connection
                          is tunneled.
                        maximum: 4294967295
                        minimum: 0
                        type: integer
                    required:
                    - targetHost
                    - targetPort
                    type: object
                type: object
              workloadSelector:
                description: Criteria used to select the specific set of pods/VMs
                  on which this `DestinationRule` configuration should be applied.
                properties:
                  matchLabels:
                    additionalProperties:
                      maxLength: 63
                      type: string
                      x-kubernetes-validations:
                      - message: wildcard not allowed in label value match
                        rule: '!self.contains(''*'')'
                    description: One or more labels that indicate a specific set of
                      pods/VMs on which a policy should be applied.
                    maxProperties: 4096
                    type: object
                    x-kubernetes-validations:
                    - message: wildcard not allowed in label key match
                      rule: self.all(key, !key.contains('*'))
                    - message: key must not be empty
                      rule: self.all(key, key.size() != 0)
                type: object
            required:
            - host
            type: object
          status:
            type: object
            x-kubernetes-preserve-unknown-fields: true
        type: object
    served: true
    storage: true
    subresources:
      status: {}
---
# Source: istio-microk8s/charts/base/templates/crds.yaml
apiVersion: apiextensions.k8s.io/v1
kind: CustomResourceDefinition
metadata:
  annotations:
    "helm.sh/resource-policy": keep
  labels:
    app: istio-pilot
    chart: istio
    heritage: Tiller
    release: istio
  name: envoyfilters.networking.istio.io
spec:
  group: networking.istio.io
  names:
    categories:
    - istio-io
    - networking-istio-io
    kind: EnvoyFilter
    listKind: EnvoyFilterList
    plural: envoyfilters
    singular: envoyfilter
  scope: Namespaced
  versions:
  - name: v1alpha3
    schema:
      openAPIV3Schema:
        properties:
          spec:
            description: 'Customizing Envoy configuration generated by Istio. See
              more details at: https://istio.io/docs/reference/config/networking/envoy-filter.html'
            properties:
              configPatches:
                description: One or more patches with match conditions.
                items:
                  properties:
                    applyTo:
                      description: |-
                        Specifies where in the Envoy configuration, the patch should be applied.

                        Valid Options: LISTENER, FILTER_CHAIN, NETWORK_FILTER, HTTP_FILTER, ROUTE_CONFIGURATION, VIRTUAL_HOST, HTTP_ROUTE, CLUSTER, EXTENSION_CONFIG, BOOTSTRAP, LISTENER_FILTER
                      enum:
                      - INVALID
                      - LISTENER
                      - FILTER_CHAIN
                      - NETWORK_FILTER
                      - HTTP_FILTER
                      - ROUTE_CONFIGURATION
                      - VIRTUAL_HOST
                      - HTTP_ROUTE
                      - CLUSTER
                      - EXTENSION_CONFIG
                      - BOOTSTRAP
                      - LISTENER_FILTER
                      type: string
                    match:
                      description: Match on listener/route configuration/cluster.
                      oneOf:
                      - not:
                          anyOf:
                          - required:
                            - listener
                          - required:
                            - routeConfiguration
                          - required:
                            - cluster
                      - required:
                        - listener
                      - required:
                        - routeConfiguration
                      - required:
                        - cluster
                      properties:
                        cluster:
                          description: Match on envoy cluster attributes.
                          properties:
                            name:
                              description: The exact name of the cluster to match.
                              type: string
                            portNumber:
                              description: The service port for which this cluster
                                was generated.
                              maximum: 4294967295
                              minimum: 0
                              type: integer
                            service:
                              description: The fully qualified service name for this
                                cluster.
                              type: string
                            subset:
                              description: The subset associated with the service.
                              type: string
                          type: object
                        context:
                          description: |-
                            The specific config generation context to match on.

                            Valid Options: ANY, SIDECAR_INBOUND, SIDECAR_OUTBOUND, GATEWAY
                          enum:
                          - ANY
                          - SIDECAR_INBOUND
                          - SIDECAR_OUTBOUND
                          - GATEWAY
                          type: string
                        listener:
                          description: Match on envoy listener attributes.
                          properties:
                            filterChain:
                              description: Match a specific filter chain in a listener.
                              properties:
                                applicationProtocols:
                                  description: Applies only to sidecars.
                                  type: string
                                destinationPort:
                                  description: The destination_port value used by
                                    a filter chain's match condition.
                                  maximum: 4294967295
                                  minimum: 0
                                  type: integer
                                filter:
                                  description: The name of a specific filter to apply
                                    the patch to.
                                  properties:
                                    name:
                                      description: The filter name to match on.
                                      type: string
                                    subFilter:
                                      description: The next level filter within this
                                        filter to match upon.
                                      properties:
                                        name:
                                          description: The filter name to match on.
                                          type: string
                                      type: object
                                  type: object
                                name:
                                  description: The name assigned to the filter chain.
                                  type: string
                                sni:
                                  description: The SNI value used by a filter chain's
                                    match condition.
                                  type: string
                                transportProtocol:
                                  description: Applies only to `SIDECAR_INBOUND` context.
                                  type: string
                              type: object
                            listenerFilter:
                              description: Match a specific listener filter.
                              type: string
                            name:
                              description: Match a specific listener by its name.
                              type: string
                            portName:
                              type: string
                            portNumber:
                              description: The service port/gateway port to which
                                traffic is being sent/received.
                              maximum: 4294967295
                              minimum: 0
                              type: integer
                          type: object
                        proxy:
                          description: Match on properties associated with a proxy.
                          properties:
                            metadata:
                              additionalProperties:
                                type: string
                              description: Match on the node metadata supplied by
                                a proxy when connecting to Istio Pilot.
                              type: object
                            proxyVersion:
                              description: A regular expression in golang regex format
                                (RE2) that can be used to select proxies using a specific
                                version of istio proxy.
                              type: string
                          type: object
                        routeConfiguration:
                          description: Match on envoy HTTP route configuration attributes.
                          properties:
                            gateway:
                              description: The Istio gateway config's namespace/name
                                for which this route configuration was generated.
                              type: string
                            name:
                              description: Route configuration name to match on.
                              type: string
                            portName:
                              description: Applicable only for GATEWAY context.
                              type: string
                            portNumber:
                              description: The service port number or gateway server
                                port number for which this route configuration was
                                generated.
                              maximum: 4294967295
                              minimum: 0
                              type: integer
                            vhost:
                              description: Match a specific virtual host in a route
                                configuration and apply the patch to the virtual host.
                              properties:
                                name:
                                  description: The VirtualHosts objects generated
                                    by Istio are named as host:port, where the host
                                    typically corresponds to the VirtualService's
                                    host field or the hostname of a service in the
                                    registry.
                                  type: string
                                route:
                                  description: Match a specific route within the virtual
                                    host.
                                  properties:
                                    action:
                                      description: |-
                                        Match a route with specific action type.

                                        Valid Options: ANY, ROUTE, REDIRECT, DIRECT_RESPONSE
                                      enum:
                                      - ANY
                                      - ROUTE
                                      - REDIRECT
                                      - DIRECT_RESPONSE
                                      type: string
                                    name:
                                      description: The Route objects generated by
                                        default are named as default.
                                      type: string
                                  type: object
                              type: object
                          type: object
                      type: object
                    patch:
                      description: The patch to apply along with the operation.
                      properties:
                        filterClass:
                          description: |-
                            Determines the filter insertion order.

                            Valid Options: AUTHN, AUTHZ, STATS
                          enum:
                          - UNSPECIFIED
                          - AUTHN
                          - AUTHZ
                          - STATS
                          type: string
                        operation:
                          description: |-
                            Determines how the patch should be applied.

                            Valid Options: MERGE, ADD, REMOVE, INSERT_BEFORE, INSERT_AFTER, INSERT_FIRST, REPLACE
                          enum:
                          - INVALID
                          - MERGE
                          - ADD
                          - REMOVE
                          - INSERT_BEFORE
                          - INSERT_AFTER
                          - INSERT_FIRST
                          - REPLACE
                          type: string
                        value:
                          description: The JSON config of the object being patched.
                          type: object
                          x-kubernetes-preserve-unknown-fields: true
                      type: object
                  type: object
                type: array
              priority:
                description: Priority defines the order in which patch sets are applied
                  within a context.
                format: int32
                type: integer
              targetRefs:
                description: Optional.
                items:
                  properties:
                    group:
                      description: group is the group of the target resource.
                      maxLength: 253
                      pattern: ^$|^[a-z0-9]([-a-z0-9]*[a-z0-9])?(\.[a-z0-9]([-a-z0-9]*[a-z0-9])?)*$
                      type: string
                    kind:
                      description: kind is kind of the target resource.
                      maxLength: 63
                      minLength: 1
                      pattern: ^[a-zA-Z]([-a-zA-Z0-9]*[a-zA-Z0-9])?$
                      type: string
                    name:
                      description: name is the name of the target resource.
                      maxLength: 253
                      minLength: 1
                      type: string
                    namespace:
                      description: namespace is the namespace of the referent.
                      type: string
                      x-kubernetes-validations:
                      - message: cross namespace referencing is not currently supported
                        rule: self.size() == 0
                  required:
                  - kind
                  - name
                  type: object
                  x-kubernetes-validations:
                  - message: Support kinds are core/Service and gateway.networking.k8s.io/Gateway
                    rule: '[self.group, self.kind] in [[''core'',''Service''], ['''',''Service''],
                      [''gateway.networking.k8s.io'',''Gateway'']]'
                type: array
              workloadSelector:
                description: Criteria used to select the specific set of pods/VMs
                  on which this patch configuration should be applied.
                properties:
                  labels:
                    additionalProperties:
                      type: string
                    description: One or more labels that indicate a specific set of
                      pods/VMs on which the configuration should be applied.
                    type: object
                type: object
            type: object
          status:
            type: object
            x-kubernetes-preserve-unknown-fields: true
        type: object
    served: true
    storage: true
    subresources:
      status: {}
---
# Source: istio-microk8s/charts/base/templates/crds.yaml
apiVersion: apiextensions.k8s.io/v1
kind: CustomResourceDefinition
metadata:
  annotations:
    "helm.sh/resource-policy": keep
  labels:
    app: istio-pilot
    chart: istio
    heritage: Tiller
    release: istio
  name: gateways.networking.istio.io
spec:
  group: networking.istio.io
  names:
    categories:
    - istio-io
    - networking-istio-io
    kind: Gateway
    listKind: GatewayList
    plural: gateways
    shortNames:
    - gw
    singular: gateway
  scope: Namespaced
  versions:
  - name: v1
    schema:
      openAPIV3Schema:
        properties:
          spec:
            description: 'Configuration affecting edge load balancer. See more details
              at: https://istio.io/docs/reference/config/networking/gateway.html'
            properties:
              selector:
                additionalProperties:
                  type: string
                description: One or more labels that indicate a specific set of pods/VMs
                  on which this gateway configuration should be applied.
                type: object
              servers:
                description: A list of server specifications.
                items:
                  properties:
                    bind:
                      description: The ip or the Unix domain socket to which the listener
                        should be bound to.
                      type: string
                    defaultEndpoint:
                      type: string
                    hosts:
                      description: One or more hosts exposed by this gateway.
                      items:
                        type: string
                      type: array
                    name:
                      description: An optional name of the server, when set must be
                        unique across all servers.
                      type: string
                    port:
                      description: The Port on which the proxy should listen for incoming
                        connections.
                      properties:
                        name:
                          description: Label assigned to the port.
                          type: string
                        number:
                          description: A valid non-negative integer port number.
                          maximum: 4294967295
                          minimum: 0
                          type: integer
                        protocol:
                          description: The protocol exposed on the port.
                          type: string
                        targetPort:
                          maximum: 4294967295
                          minimum: 0
                          type: integer
                      required:
                      - number
                      - protocol
                      - name
                      type: object
                    tls:
                      description: Set of TLS related options that govern the server's
                        behavior.
                      properties:
                        caCertificates:
                          description: REQUIRED if mode is `MUTUAL` or `OPTIONAL_MUTUAL`.
                          type: string
                        caCrl:
                          description: 'OPTIONAL: The path to the file containing
                            the certificate revocation list (CRL) to use in verifying
                            a presented client side certificate.'
                          type: string
                        cipherSuites:
                          description: 'Optional: If specified, only support the specified
                            cipher list.'
                          items:
                            type: string
                          type: array
                        credentialName:
                          description: For gateways running on Kubernetes, the name
                            of the secret that holds the TLS certs including the CA
                            certificates.
                          type: string
                        httpsRedirect:
                          description: If set to true, the load balancer will send
                            a 301 redirect for all http connections, asking the clients
                            to use HTTPS.
                          type: boolean
                        maxProtocolVersion:
                          description: |-
                            Optional: Maximum TLS protocol version.

                            Valid Options: TLS_AUTO, TLSV1_0, TLSV1_1, TLSV1_2, TLSV1_3
                          enum:
                          - TLS_AUTO
                          - TLSV1_0
                          - TLSV1_1
                          - TLSV1_2
                          - TLSV1_3
                          type: string
                        minProtocolVersion:
                          description: |-
                            Optional: Minimum TLS protocol version.

                            Valid Options: TLS_AUTO, TLSV1_0, TLSV1_1, TLSV1_2, TLSV1_3
                          enum:
                          - TLS_AUTO
                          - TLSV1_0
                          - TLSV1_1
                          - TLSV1_2
                          - TLSV1_3
                          type: string
                        mode:
                          description: |-
                            Optional: Indicates whether connections to this port should be secured using TLS.

                            Valid Options: PASSTHROUGH, SIMPLE, MUTUAL, AUTO_PASSTHROUGH, ISTIO_MUTUAL, OPTIONAL_MUTUAL
                          enum:
                          - PASSTHROUGH
                          - SIMPLE
                          - MUTUAL
                          - AUTO_PASSTHROUGH
                          - ISTIO_MUTUAL
                          - OPTIONAL_MUTUAL
                          type: string
                        privateKey:
                          description: REQUIRED if mode is `SIMPLE` or `MUTUAL`.
                          type: string
                        serverCertificate:
                          description: REQUIRED if mode is `SIMPLE` or `MUTUAL`.
                          type: string
                        subjectAltNames:
                          description: A list of alternate names to verify the subject
                            identity in the certificate presented by the client.
                          items:
                            type: string
                          type: array
                        verifyCertificateHash:
                          description: An optional list of hex-encoded SHA-256 hashes
                            of the authorized client certificates.
                          items:
                            type: string
                          type: array
                        verifyCertificateSpki:
                          description: An optional list of base64-encoded SHA-256
                            hashes of the SPKIs of authorized client certificates.
                          items:
                            type: string
                          type: array
                      type: object
                  required:
                  - port
                  - hosts
                  type: object
                type: array
            type: object
          status:
            type: object
            x-kubernetes-preserve-unknown-fields: true
        type: object
    served: true
    storage: false
    subresources:
      status: {}
  - name: v1alpha3
    schema:
      openAPIV3Schema:
        properties:
          spec:
            description: 'Configuration affecting edge load balancer. See more details
              at: https://istio.io/docs/reference/config/networking/gateway.html'
            properties:
              selector:
                additionalProperties:
                  type: string
                description: One or more labels that indicate a specific set of pods/VMs
                  on which this gateway configuration should be applied.
                type: object
              servers:
                description: A list of server specifications.
                items:
                  properties:
                    bind:
                      description: The ip or the Unix domain socket to which the listener
                        should be bound to.
                      type: string
                    defaultEndpoint:
                      type: string
                    hosts:
                      description: One or more hosts exposed by this gateway.
                      items:
                        type: string
                      type: array
                    name:
                      description: An optional name of the server, when set must be
                        unique across all servers.
                      type: string
                    port:
                      description: The Port on which the proxy should listen for incoming
                        connections.
                      properties:
                        name:
                          description: Label assigned to the port.
                          type: string
                        number:
                          description: A valid non-negative integer port number.
                          maximum: 4294967295
                          minimum: 0
                          type: integer
                        protocol:
                          description: The protocol exposed on the port.
                          type: string
                        targetPort:
                          maximum: 4294967295
                          minimum: 0
                          type: integer
                      required:
                      - number
                      - protocol
                      - name
                      type: object
                    tls:
                      description: Set of TLS related options that govern the server's
                        behavior.
                      properties:
                        caCertificates:
                          description: REQUIRED if mode is `MUTUAL` or `OPTIONAL_MUTUAL`.
                          type: string
                        caCrl:
                          description: 'OPTIONAL: The path to the file containing
                            the certificate revocation list (CRL) to use in verifying
                            a presented client side certificate.'
                          type: string
                        cipherSuites:
                          description: 'Optional: If specified, only support the specified
                            cipher list.'
                          items:
                            type: string
                          type: array
                        credentialName:
                          description: For gateways running on Kubernetes, the name
                            of the secret that holds the TLS certs including the CA
                            certificates.
                          type: string
                        httpsRedirect:
                          description: If set to true, the load balancer will send
                            a 301 redirect for all http connections, asking the clients
                            to use HTTPS.
                          type: boolean
                        maxProtocolVersion:
                          description: |-
                            Optional: Maximum TLS protocol version.

                            Valid Options: TLS_AUTO, TLSV1_0, TLSV1_1, TLSV1_2, TLSV1_3
                          enum:
                          - TLS_AUTO
                          - TLSV1_0
                          - TLSV1_1
                          - TLSV1_2
                          - TLSV1_3
                          type: string
                        minProtocolVersion:
                          description: |-
                            Optional: Minimum TLS protocol version.

                            Valid Options: TLS_AUTO, TLSV1_0, TLSV1_1, TLSV1_2, TLSV1_3
                          enum:
                          - TLS_AUTO
                          - TLSV1_0
                          - TLSV1_1
                          - TLSV1_2
                          - TLSV1_3
                          type: string
                        mode:
                          description: |-
                            Optional: Indicates whether connections to this port should be secured using TLS.

                            Valid Options: PASSTHROUGH, SIMPLE, MUTUAL, AUTO_PASSTHROUGH, ISTIO_MUTUAL, OPTIONAL_MUTUAL
                          enum:
                          - PASSTHROUGH
                          - SIMPLE
                          - MUTUAL
                          - AUTO_PASSTHROUGH
                          - ISTIO_MUTUAL
                          - OPTIONAL_MUTUAL
                          type: string
                        privateKey:
                          description: REQUIRED if mode is `SIMPLE` or `MUTUAL`.
                          type: string
                        serverCertificate:
                          description: REQUIRED if mode is `SIMPLE` or `MUTUAL`.
                          type: string
                        subjectAltNames:
                          description: A list of alternate names to verify the subject
                            identity in the certificate presented by the client.
                          items:
                            type: string
                          type: array
                        verifyCertificateHash:
                          description: An optional list of hex-encoded SHA-256 hashes
                            of the authorized client certificates.
                          items:
                            type: string
                          type: array
                        verifyCertificateSpki:
                          description: An optional list of base64-encoded SHA-256
                            hashes of the SPKIs of authorized client certificates.
                          items:
                            type: string
                          type: array
                      type: object
                  required:
                  - port
                  - hosts
                  type: object
                type: array
            type: object
          status:
            type: object
            x-kubernetes-preserve-unknown-fields: true
        type: object
    served: true
    storage: false
    subresources:
      status: {}
  - name: v1beta1
    schema:
      openAPIV3Schema:
        properties:
          spec:
            description: 'Configuration affecting edge load balancer. See more details
              at: https://istio.io/docs/reference/config/networking/gateway.html'
            properties:
              selector:
                additionalProperties:
                  type: string
                description: One or more labels that indicate a specific set of pods/VMs
                  on which this gateway configuration should be applied.
                type: object
              servers:
                description: A list of server specifications.
                items:
                  properties:
                    bind:
                      description: The ip or the Unix domain socket to which the listener
                        should be bound to.
                      type: string
                    defaultEndpoint:
                      type: string
                    hosts:
                      description: One or more hosts exposed by this gateway.
                      items:
                        type: string
                      type: array
                    name:
                      description: An optional name of the server, when set must be
                        unique across all servers.
                      type: string
                    port:
                      description: The Port on which the proxy should listen for incoming
                        connections.
                      properties:
                        name:
                          description: Label assigned to the port.
                          type: string
                        number:
                          description: A valid non-negative integer port number.
                          maximum: 4294967295
                          minimum: 0
                          type: integer
                        protocol:
                          description: The protocol exposed on the port.
                          type: string
                        targetPort:
                          maximum: 4294967295
                          minimum: 0
                          type: integer
                      required:
                      - number
                      - protocol
                      - name
                      type: object
                    tls:
                      description: Set of TLS related options that govern the server's
                        behavior.
                      properties:
                        caCertificates:
                          description: REQUIRED if mode is `MUTUAL` or `OPTIONAL_MUTUAL`.
                          type: string
                        caCrl:
                          description: 'OPTIONAL: The path to the file containing
                            the certificate revocation list (CRL) to use in verifying
                            a presented client side certificate.'
                          type: string
                        cipherSuites:
                          description: 'Optional: If specified, only support the specified
                            cipher list.'
                          items:
                            type: string
                          type: array
                        credentialName:
                          description: For gateways running on Kubernetes, the name
                            of the secret that holds the TLS certs including the CA
                            certificates.
                          type: string
                        httpsRedirect:
                          description: If set to true, the load balancer will send
                            a 301 redirect for all http connections, asking the clients
                            to use HTTPS.
                          type: boolean
                        maxProtocolVersion:
                          description: |-
                            Optional: Maximum TLS protocol version.

                            Valid Options: TLS_AUTO, TLSV1_0, TLSV1_1, TLSV1_2, TLSV1_3
                          enum:
                          - TLS_AUTO
                          - TLSV1_0
                          - TLSV1_1
                          - TLSV1_2
                          - TLSV1_3
                          type: string
                        minProtocolVersion:
                          description: |-
                            Optional: Minimum TLS protocol version.

                            Valid Options: TLS_AUTO, TLSV1_0, TLSV1_1, TLSV1_2, TLSV1_3
                          enum:
                          - TLS_AUTO
                          - TLSV1_0
                          - TLSV1_1
                          - TLSV1_2
                          - TLSV1_3
                          type: string
                        mode:
                          description: |-
                            Optional: Indicates whether connections to this port should be secured using TLS.

                            Valid Options: PASSTHROUGH, SIMPLE, MUTUAL, AUTO_PASSTHROUGH, ISTIO_MUTUAL, OPTIONAL_MUTUAL
                          enum:
                          - PASSTHROUGH
                          - SIMPLE
                          - MUTUAL
                          - AUTO_PASSTHROUGH
                          - ISTIO_MUTUAL
                          - OPTIONAL_MUTUAL
                          type: string
                        privateKey:
                          description: REQUIRED if mode is `SIMPLE` or `MUTUAL`.
                          type: string
                        serverCertificate:
                          description: REQUIRED if mode is `SIMPLE` or `MUTUAL`.
                          type: string
                        subjectAltNames:
                          description: A list of alternate names to verify the subject
                            identity in the certificate presented by the client.
                          items:
                            type: string
                          type: array
                        verifyCertificateHash:
                          description: An optional list of hex-encoded SHA-256 hashes
                            of the authorized client certificates.
                          items:
                            type: string
                          type: array
                        verifyCertificateSpki:
                          description: An optional list of base64-encoded SHA-256
                            hashes of the SPKIs of authorized client certificates.
                          items:
                            type: string
                          type: array
                      type: object
                  required:
                  - port
                  - hosts
                  type: object
                type: array
            type: object
          status:
            type: object
            x-kubernetes-preserve-unknown-fields: true
        type: object
    served: true
    storage: true
    subresources:
      status: {}
---
# Source: istio-microk8s/charts/base/templates/crds.yaml
apiVersion: apiextensions.k8s.io/v1
kind: CustomResourceDefinition
metadata:
  annotations:
    "helm.sh/resource-policy": keep
  labels:
    app: istio-pilot
    chart: istio
    heritage: Tiller
    release: istio
  name: proxyconfigs.networking.istio.io
spec:
  group: networking.istio.io
  names:
    categories:
    - istio-io
    - networking-istio-io
    kind: ProxyConfig
    listKind: ProxyConfigList
    plural: proxyconfigs
    singular: proxyconfig
  scope: Namespaced
  versions:
  - name: v1beta1
    schema:
      openAPIV3Schema:
        properties:
          spec:
            description: 'Provides configuration for individual workloads. See more
              details at: https://istio.io/docs/reference/config/networking/proxy-config.html'
            properties:
              concurrency:
                description: The number of worker threads to run.
                format: int32
                minimum: 0
                nullable: true
                type: integer
              environmentVariables:
                additionalProperties:
                  maxLength: 2048
                  type: string
                description: Additional environment variables for the proxy.
                type: object
              image:
                description: Specifies the details of the proxy image.
                properties:
                  imageType:
                    description: The image type of the image.
                    type: string
                type: object
              selector:
                description: Optional.
                properties:
                  matchLabels:
                    additionalProperties:
                      maxLength: 63
                      type: string
                      x-kubernetes-validations:
                      - message: wildcard not allowed in label value match
                        rule: '!self.contains(''*'')'
                    description: One or more labels that indicate a specific set of
                      pods/VMs on which a policy should be applied.
                    maxProperties: 4096
                    type: object
                    x-kubernetes-validations:
                    - message: wildcard not allowed in label key match
                      rule: self.all(key, !key.contains('*'))
                    - message: key must not be empty
                      rule: self.all(key, key.size() != 0)
                type: object
            type: object
          status:
            type: object
            x-kubernetes-preserve-unknown-fields: true
        type: object
    served: true
    storage: true
    subresources:
      status: {}
---
# Source: istio-microk8s/charts/base/templates/crds.yaml
apiVersion: apiextensions.k8s.io/v1
kind: CustomResourceDefinition
metadata:
  annotations:
    "helm.sh/resource-policy": keep
  labels:
    app: istio-pilot
    chart: istio
    heritage: Tiller
    release: istio
  name: serviceentries.networking.istio.io
spec:
  group: networking.istio.io
  names:
    categories:
    - istio-io
    - networking-istio-io
    kind: ServiceEntry
    listKind: ServiceEntryList
    plural: serviceentries
    shortNames:
    - se
    singular: serviceentry
  scope: Namespaced
  versions:
  - additionalPrinterColumns:
    - description: The hosts associated with the ServiceEntry
      jsonPath: .spec.hosts
      name: Hosts
      type: string
    - description: Whether the service is external to the mesh or part of the mesh
        (MESH_EXTERNAL or MESH_INTERNAL)
      jsonPath: .spec.location
      name: Location
      type: string
    - description: Service resolution mode for the hosts (NONE, STATIC, or DNS)
      jsonPath: .spec.resolution
      name: Resolution
      type: string
    - description: 'CreationTimestamp is a timestamp representing the server time
        when this object was created. It is not guaranteed to be set in happens-before
        order across separate operations. Clients may not set this value. It is represented
        in RFC3339 form and is in UTC. Populated by the system. Read-only. Null for
        lists. More info: https://git.k8s.io/community/contributors/devel/api-conventions.md#metadata'
      jsonPath: .metadata.creationTimestamp
      name: Age
      type: date
    name: v1
    schema:
      openAPIV3Schema:
        properties:
          spec:
            description: 'Configuration affecting service registry. See more details
              at: https://istio.io/docs/reference/config/networking/service-entry.html'
            properties:
              addresses:
                description: The virtual IP addresses associated with the service.
                items:
                  type: string
                type: array
              endpoints:
                description: One or more endpoints associated with the service.
                items:
                  properties:
                    address:
                      description: Address associated with the network endpoint without
                        the port.
                      maxLength: 256
                      type: string
                      x-kubernetes-validations:
                      - message: UDS must be an absolute path or abstract socket
                        rule: 'self.startsWith(''unix://'') ? (self.substring(7,8)
                          == ''/'' || self.substring(7,8) == ''@'') : true'
                      - message: UDS may not be a dir
                        rule: 'self.startsWith(''unix://'') ? !self.endsWith(''/'')
                          : true'
                    labels:
                      additionalProperties:
                        type: string
                      description: One or more labels associated with the endpoint.
                      maxProperties: 256
                      type: object
                    locality:
                      description: The locality associated with the endpoint.
                      maxLength: 2048
                      type: string
                    network:
                      description: Network enables Istio to group endpoints resident
                        in the same L3 domain/network.
                      maxLength: 2048
                      type: string
                    ports:
                      additionalProperties:
                        maximum: 4294967295
                        minimum: 0
                        type: integer
                        x-kubernetes-validations:
                        - message: port must be between 1-65535
                          rule: 0 < self && self <= 65535
                      description: Set of ports associated with the endpoint.
                      maxProperties: 128
                      type: object
                      x-kubernetes-validations:
                      - message: port name must be valid
                        rule: self.all(key, size(key) < 63 && key.matches('^[a-zA-Z0-9](?:[-a-zA-Z0-9]*[a-zA-Z0-9])?$'))
                    serviceAccount:
                      description: The service account associated with the workload
                        if a sidecar is present in the workload.
                      maxLength: 253
                      type: string
                    weight:
                      description: The load balancing weight associated with the endpoint.
                      maximum: 4294967295
                      minimum: 0
                      type: integer
                  type: object
                  x-kubernetes-validations:
                  - message: Address is required
                    rule: has(self.address) || has(self.network)
                  - message: UDS may not include ports
                    rule: '(has(self.address) && self.address.startsWith(''unix://''))
                      ? !has(self.ports) : true'
                maxItems: 4096
                type: array
              exportTo:
                description: A list of namespaces to which this service is exported.
                items:
                  type: string
                type: array
              hosts:
                description: The hosts associated with the ServiceEntry.
                items:
                  type: string
                type: array
              location:
                description: |-
                  Specify whether the service should be considered external to the mesh or part of the mesh.

                  Valid Options: MESH_EXTERNAL, MESH_INTERNAL
                enum:
                - MESH_EXTERNAL
                - MESH_INTERNAL
                type: string
              ports:
                description: The ports associated with the external service.
                items:
                  properties:
                    name:
                      description: Label assigned to the port.
                      type: string
                    number:
                      description: A valid non-negative integer port number.
                      maximum: 4294967295
                      minimum: 0
                      type: integer
                    protocol:
                      description: The protocol exposed on the port.
                      type: string
                    targetPort:
                      description: The port number on the endpoint where the traffic
                        will be received.
                      maximum: 4294967295
                      minimum: 0
                      type: integer
                  required:
                  - number
                  - name
                  type: object
                type: array
              resolution:
                description: |-
                  Service resolution mode for the hosts.

                  Valid Options: NONE, STATIC, DNS, DNS_ROUND_ROBIN
                enum:
                - NONE
                - STATIC
                - DNS
                - DNS_ROUND_ROBIN
                type: string
              subjectAltNames:
                description: If specified, the proxy will verify that the server certificate's
                  subject alternate name matches one of the specified values.
                items:
                  type: string
                type: array
              workloadSelector:
                description: Applicable only for MESH_INTERNAL services.
                properties:
                  labels:
                    additionalProperties:
                      type: string
                    description: One or more labels that indicate a specific set of
                      pods/VMs on which the configuration should be applied.
                    type: object
                type: object
            required:
            - hosts
            type: object
          status:
            type: object
            x-kubernetes-preserve-unknown-fields: true
        type: object
    served: true
    storage: false
    subresources:
      status: {}
  - additionalPrinterColumns:
    - description: The hosts associated with the ServiceEntry
      jsonPath: .spec.hosts
      name: Hosts
      type: string
    - description: Whether the service is external to the mesh or part of the mesh
        (MESH_EXTERNAL or MESH_INTERNAL)
      jsonPath: .spec.location
      name: Location
      type: string
    - description: Service resolution mode for the hosts (NONE, STATIC, or DNS)
      jsonPath: .spec.resolution
      name: Resolution
      type: string
    - description: 'CreationTimestamp is a timestamp representing the server time
        when this object was created. It is not guaranteed to be set in happens-before
        order across separate operations. Clients may not set this value. It is represented
        in RFC3339 form and is in UTC. Populated by the system. Read-only. Null for
        lists. More info: https://git.k8s.io/community/contributors/devel/api-conventions.md#metadata'
      jsonPath: .metadata.creationTimestamp
      name: Age
      type: date
    name: v1alpha3
    schema:
      openAPIV3Schema:
        properties:
          spec:
            description: 'Configuration affecting service registry. See more details
              at: https://istio.io/docs/reference/config/networking/service-entry.html'
            properties:
              addresses:
                description: The virtual IP addresses associated with the service.
                items:
                  type: string
                type: array
              endpoints:
                description: One or more endpoints associated with the service.
                items:
                  properties:
                    address:
                      description: Address associated with the network endpoint without
                        the port.
                      maxLength: 256
                      type: string
                      x-kubernetes-validations:
                      - message: UDS must be an absolute path or abstract socket
                        rule: 'self.startsWith(''unix://'') ? (self.substring(7,8)
                          == ''/'' || self.substring(7,8) == ''@'') : true'
                      - message: UDS may not be a dir
                        rule: 'self.startsWith(''unix://'') ? !self.endsWith(''/'')
                          : true'
                    labels:
                      additionalProperties:
                        type: string
                      description: One or more labels associated with the endpoint.
                      maxProperties: 256
                      type: object
                    locality:
                      description: The locality associated with the endpoint.
                      maxLength: 2048
                      type: string
                    network:
                      description: Network enables Istio to group endpoints resident
                        in the same L3 domain/network.
                      maxLength: 2048
                      type: string
                    ports:
                      additionalProperties:
                        maximum: 4294967295
                        minimum: 0
                        type: integer
                        x-kubernetes-validations:
                        - message: port must be between 1-65535
                          rule: 0 < self && self <= 65535
                      description: Set of ports associated with the endpoint.
                      maxProperties: 128
                      type: object
                      x-kubernetes-validations:
                      - message: port name must be valid
                        rule: self.all(key, size(key) < 63 && key.matches('^[a-zA-Z0-9](?:[-a-zA-Z0-9]*[a-zA-Z0-9])?$'))
                    serviceAccount:
                      description: The service account associated with the workload
                        if a sidecar is present in the workload.
                      maxLength: 253
                      type: string
                    weight:
                      description: The load balancing weight associated with the endpoint.
                      maximum: 4294967295
                      minimum: 0
                      type: integer
                  type: object
                  x-kubernetes-validations:
                  - message: Address is required
                    rule: has(self.address) || has(self.network)
                  - message: UDS may not include ports
                    rule: '(has(self.address) && self.address.startsWith(''unix://''))
                      ? !has(self.ports) : true'
                maxItems: 4096
                type: array
              exportTo:
                description: A list of namespaces to which this service is exported.
                items:
                  type: string
                type: array
              hosts:
                description: The hosts associated with the ServiceEntry.
                items:
                  type: string
                type: array
              location:
                description: |-
                  Specify whether the service should be considered external to the mesh or part of the mesh.

                  Valid Options: MESH_EXTERNAL, MESH_INTERNAL
                enum:
                - MESH_EXTERNAL
                - MESH_INTERNAL
                type: string
              ports:
                description: The ports associated with the external service.
                items:
                  properties:
                    name:
                      description: Label assigned to the port.
                      type: string
                    number:
                      description: A valid non-negative integer port number.
                      maximum: 4294967295
                      minimum: 0
                      type: integer
                    protocol:
                      description: The protocol exposed on the port.
                      type: string
                    targetPort:
                      description: The port number on the endpoint where the traffic
                        will be received.
                      maximum: 4294967295
                      minimum: 0
                      type: integer
                  required:
                  - number
                  - name
                  type: object
                type: array
              resolution:
                description: |-
                  Service resolution mode for the hosts.

                  Valid Options: NONE, STATIC, DNS, DNS_ROUND_ROBIN
                enum:
                - NONE
                - STATIC
                - DNS
                - DNS_ROUND_ROBIN
                type: string
              subjectAltNames:
                description: If specified, the proxy will verify that the server certificate's
                  subject alternate name matches one of the specified values.
                items:
                  type: string
                type: array
              workloadSelector:
                description: Applicable only for MESH_INTERNAL services.
                properties:
                  labels:
                    additionalProperties:
                      type: string
                    description: One or more labels that indicate a specific set of
                      pods/VMs on which the configuration should be applied.
                    type: object
                type: object
            required:
            - hosts
            type: object
          status:
            type: object
            x-kubernetes-preserve-unknown-fields: true
        type: object
    served: true
    storage: false
    subresources:
      status: {}
  - additionalPrinterColumns:
    - description: The hosts associated with the ServiceEntry
      jsonPath: .spec.hosts
      name: Hosts
      type: string
    - description: Whether the service is external to the mesh or part of the mesh
        (MESH_EXTERNAL or MESH_INTERNAL)
      jsonPath: .spec.location
      name: Location
      type: string
    - description: Service resolution mode for the hosts (NONE, STATIC, or DNS)
      jsonPath: .spec.resolution
      name: Resolution
      type: string
    - description: 'CreationTimestamp is a timestamp representing the server time
        when this object was created. It is not guaranteed to be set in happens-before
        order across separate operations. Clients may not set this value. It is represented
        in RFC3339 form and is in UTC. Populated by the system. Read-only. Null for
        lists. More info: https://git.k8s.io/community/contributors/devel/api-conventions.md#metadata'
      jsonPath: .metadata.creationTimestamp
      name: Age
      type: date
    name: v1beta1
    schema:
      openAPIV3Schema:
        properties:
          spec:
            description: 'Configuration affecting service registry. See more details
              at: https://istio.io/docs/reference/config/networking/service-entry.html'
            properties:
              addresses:
                description: The virtual IP addresses associated with the service.
                items:
                  type: string
                type: array
              endpoints:
                description: One or more endpoints associated with the service.
                items:
                  properties:
                    address:
                      description: Address associated with the network endpoint without
                        the port.
                      maxLength: 256
                      type: string
                      x-kubernetes-validations:
                      - message: UDS must be an absolute path or abstract socket
                        rule: 'self.startsWith(''unix://'') ? (self.substring(7,8)
                          == ''/'' || self.substring(7,8) == ''@'') : true'
                      - message: UDS may not be a dir
                        rule: 'self.startsWith(''unix://'') ? !self.endsWith(''/'')
                          : true'
                    labels:
                      additionalProperties:
                        type: string
                      description: One or more labels associated with the endpoint.
                      maxProperties: 256
                      type: object
                    locality:
                      description: The locality associated with the endpoint.
                      maxLength: 2048
                      type: string
                    network:
                      description: Network enables Istio to group endpoints resident
                        in the same L3 domain/network.
                      maxLength: 2048
                      type: string
                    ports:
                      additionalProperties:
                        maximum: 4294967295
                        minimum: 0
                        type: integer
                        x-kubernetes-validations:
                        - message: port must be between 1-65535
                          rule: 0 < self && self <= 65535
                      description: Set of ports associated with the endpoint.
                      maxProperties: 128
                      type: object
                      x-kubernetes-validations:
                      - message: port name must be valid
                        rule: self.all(key, size(key) < 63 && key.matches('^[a-zA-Z0-9](?:[-a-zA-Z0-9]*[a-zA-Z0-9])?$'))
                    serviceAccount:
                      description: The service account associated with the workload
                        if a sidecar is present in the workload.
                      maxLength: 253
                      type: string
                    weight:
                      description: The load balancing weight associated with the endpoint.
                      maximum: 4294967295
                      minimum: 0
                      type: integer
                  type: object
                  x-kubernetes-validations:
                  - message: Address is required
                    rule: has(self.address) || has(self.network)
                  - message: UDS may not include ports
                    rule: '(has(self.address) && self.address.startsWith(''unix://''))
                      ? !has(self.ports) : true'
                maxItems: 4096
                type: array
              exportTo:
                description: A list of namespaces to which this service is exported.
                items:
                  type: string
                type: array
              hosts:
                description: The hosts associated with the ServiceEntry.
                items:
                  type: string
                type: array
              location:
                description: |-
                  Specify whether the service should be considered external to the mesh or part of the mesh.

                  Valid Options: MESH_EXTERNAL, MESH_INTERNAL
                enum:
                - MESH_EXTERNAL
                - MESH_INTERNAL
                type: string
              ports:
                description: The ports associated with the external service.
                items:
                  properties:
                    name:
                      description: Label assigned to the port.
                      type: string
                    number:
                      description: A valid non-negative integer port number.
                      maximum: 4294967295
                      minimum: 0
                      type: integer
                    protocol:
                      description: The protocol exposed on the port.
                      type: string
                    targetPort:
                      description: The port number on the endpoint where the traffic
                        will be received.
                      maximum: 4294967295
                      minimum: 0
                      type: integer
                  required:
                  - number
                  - name
                  type: object
                type: array
              resolution:
                description: |-
                  Service resolution mode for the hosts.

                  Valid Options: NONE, STATIC, DNS, DNS_ROUND_ROBIN
                enum:
                - NONE
                - STATIC
                - DNS
                - DNS_ROUND_ROBIN
                type: string
              subjectAltNames:
                description: If specified, the proxy will verify that the server certificate's
                  subject alternate name matches one of the specified values.
                items:
                  type: string
                type: array
              workloadSelector:
                description: Applicable only for MESH_INTERNAL services.
                properties:
                  labels:
                    additionalProperties:
                      type: string
                    description: One or more labels that indicate a specific set of
                      pods/VMs on which the configuration should be applied.
                    type: object
                type: object
            required:
            - hosts
            type: object
          status:
            type: object
            x-kubernetes-preserve-unknown-fields: true
        type: object
    served: true
    storage: true
    subresources:
      status: {}
---
# Source: istio-microk8s/charts/base/templates/crds.yaml
apiVersion: apiextensions.k8s.io/v1
kind: CustomResourceDefinition
metadata:
  annotations:
    "helm.sh/resource-policy": keep
  labels:
    app: istio-pilot
    chart: istio
    heritage: Tiller
    release: istio
  name: sidecars.networking.istio.io
spec:
  group: networking.istio.io
  names:
    categories:
    - istio-io
    - networking-istio-io
    kind: Sidecar
    listKind: SidecarList
    plural: sidecars
    singular: sidecar
  scope: Namespaced
  versions:
  - name: v1
    schema:
      openAPIV3Schema:
        properties:
          spec:
            description: 'Configuration affecting network reachability of a sidecar.
              See more details at: https://istio.io/docs/reference/config/networking/sidecar.html'
            properties:
              egress:
                description: Egress specifies the configuration of the sidecar for
                  processing outbound traffic from the attached workload instance
                  to other services in the mesh.
                items:
                  properties:
                    bind:
                      description: The IP(IPv4 or IPv6) or the Unix domain socket
                        to which the listener should be bound to.
                      type: string
                    captureMode:
                      description: |-
                        When the bind address is an IP, the captureMode option dictates how traffic to the listener is expected to be captured (or not).

                        Valid Options: DEFAULT, IPTABLES, NONE
                      enum:
                      - DEFAULT
                      - IPTABLES
                      - NONE
                      type: string
                    hosts:
                      description: One or more service hosts exposed by the listener
                        in `namespace/dnsName` format.
                      items:
                        type: string
                      type: array
                    port:
                      description: The port associated with the listener.
                      properties:
                        name:
                          description: Label assigned to the port.
                          type: string
                        number:
                          description: A valid non-negative integer port number.
                          maximum: 4294967295
                          minimum: 0
                          type: integer
                        protocol:
                          description: The protocol exposed on the port.
                          type: string
                        targetPort:
                          maximum: 4294967295
                          minimum: 0
                          type: integer
                      type: object
                  required:
                  - hosts
                  type: object
                type: array
              inboundConnectionPool:
                description: Settings controlling the volume of connections Envoy
                  will accept from the network.
                properties:
                  http:
                    description: HTTP connection pool settings.
                    properties:
                      h2UpgradePolicy:
                        description: |-
                          Specify if http1.1 connection should be upgraded to http2 for the associated destination.

                          Valid Options: DEFAULT, DO_NOT_UPGRADE, UPGRADE
                        enum:
                        - DEFAULT
                        - DO_NOT_UPGRADE
                        - UPGRADE
                        type: string
                      http1MaxPendingRequests:
                        description: Maximum number of requests that will be queued
                          while waiting for a ready connection pool connection.
                        format: int32
                        type: integer
                      http2MaxRequests:
                        description: Maximum number of active requests to a destination.
                        format: int32
                        type: integer
                      idleTimeout:
                        description: The idle timeout for upstream connection pool
                          connections.
                        type: string
                        x-kubernetes-validations:
                        - message: must be a valid duration greater than 1ms
                          rule: duration(self) >= duration('1ms')
                      maxConcurrentStreams:
                        description: The maximum number of concurrent streams allowed
                          for a peer on one HTTP/2 connection.
                        format: int32
                        type: integer
                      maxRequestsPerConnection:
                        description: Maximum number of requests per connection to
                          a backend.
                        format: int32
                        type: integer
                      maxRetries:
                        description: Maximum number of retries that can be outstanding
                          to all hosts in a cluster at a given time.
                        format: int32
                        type: integer
                      useClientProtocol:
                        description: If set to true, client protocol will be preserved
                          while initiating connection to backend.
                        type: boolean
                    type: object
                  tcp:
                    description: Settings common to both HTTP and TCP upstream connections.
                    properties:
                      connectTimeout:
                        description: TCP connection timeout.
                        type: string
                        x-kubernetes-validations:
                        - message: must be a valid duration greater than 1ms
                          rule: duration(self) >= duration('1ms')
                      idleTimeout:
                        description: The idle timeout for TCP connections.
                        type: string
                        x-kubernetes-validations:
                        - message: must be a valid duration greater than 1ms
                          rule: duration(self) >= duration('1ms')
                      maxConnectionDuration:
                        description: The maximum duration of a connection.
                        type: string
                        x-kubernetes-validations:
                        - message: must be a valid duration greater than 1ms
                          rule: duration(self) >= duration('1ms')
                      maxConnections:
                        description: Maximum number of HTTP1 /TCP connections to a
                          destination host.
                        format: int32
                        type: integer
                      tcpKeepalive:
                        description: If set then set SO_KEEPALIVE on the socket to
                          enable TCP Keepalives.
                        properties:
                          interval:
                            description: The time duration between keep-alive probes.
                            type: string
                            x-kubernetes-validations:
                            - message: must be a valid duration greater than 1ms
                              rule: duration(self) >= duration('1ms')
                          probes:
                            description: Maximum number of keepalive probes to send
                              without response before deciding the connection is dead.
                            maximum: 4294967295
                            minimum: 0
                            type: integer
                          time:
                            description: The time duration a connection needs to be
                              idle before keep-alive probes start being sent.
                            type: string
                            x-kubernetes-validations:
                            - message: must be a valid duration greater than 1ms
                              rule: duration(self) >= duration('1ms')
                        type: object
                    type: object
                type: object
              ingress:
                description: Ingress specifies the configuration of the sidecar for
                  processing inbound traffic to the attached workload instance.
                items:
                  properties:
                    bind:
                      description: The IP(IPv4 or IPv6) to which the listener should
                        be bound.
                      type: string
                    captureMode:
                      description: |-
                        The captureMode option dictates how traffic to the listener is expected to be captured (or not).

                        Valid Options: DEFAULT, IPTABLES, NONE
                      enum:
                      - DEFAULT
                      - IPTABLES
                      - NONE
                      type: string
                    connectionPool:
                      description: Settings controlling the volume of connections
                        Envoy will accept from the network.
                      properties:
                        http:
                          description: HTTP connection pool settings.
                          properties:
                            h2UpgradePolicy:
                              description: |-
                                Specify if http1.1 connection should be upgraded to http2 for the associated destination.

                                Valid Options: DEFAULT, DO_NOT_UPGRADE, UPGRADE
                              enum:
                              - DEFAULT
                              - DO_NOT_UPGRADE
                              - UPGRADE
                              type: string
                            http1MaxPendingRequests:
                              description: Maximum number of requests that will be
                                queued while waiting for a ready connection pool connection.
                              format: int32
                              type: integer
                            http2MaxRequests:
                              description: Maximum number of active requests to a
                                destination.
                              format: int32
                              type: integer
                            idleTimeout:
                              description: The idle timeout for upstream connection
                                pool connections.
                              type: string
                              x-kubernetes-validations:
                              - message: must be a valid duration greater than 1ms
                                rule: duration(self) >= duration('1ms')
                            maxConcurrentStreams:
                              description: The maximum number of concurrent streams
                                allowed for a peer on one HTTP/2 connection.
                              format: int32
                              type: integer
                            maxRequestsPerConnection:
                              description: Maximum number of requests per connection
                                to a backend.
                              format: int32
                              type: integer
                            maxRetries:
                              description: Maximum number of retries that can be outstanding
                                to all hosts in a cluster at a given time.
                              format: int32
                              type: integer
                            useClientProtocol:
                              description: If set to true, client protocol will be
                                preserved while initiating connection to backend.
                              type: boolean
                          type: object
                        tcp:
                          description: Settings common to both HTTP and TCP upstream
                            connections.
                          properties:
                            connectTimeout:
                              description: TCP connection timeout.
                              type: string
                              x-kubernetes-validations:
                              - message: must be a valid duration greater than 1ms
                                rule: duration(self) >= duration('1ms')
                            idleTimeout:
                              description: The idle timeout for TCP connections.
                              type: string
                              x-kubernetes-validations:
                              - message: must be a valid duration greater than 1ms
                                rule: duration(self) >= duration('1ms')
                            maxConnectionDuration:
                              description: The maximum duration of a connection.
                              type: string
                              x-kubernetes-validations:
                              - message: must be a valid duration greater than 1ms
                                rule: duration(self) >= duration('1ms')
                            maxConnections:
                              description: Maximum number of HTTP1 /TCP connections
                                to a destination host.
                              format: int32
                              type: integer
                            tcpKeepalive:
                              description: If set then set SO_KEEPALIVE on the socket
                                to enable TCP Keepalives.
                              properties:
                                interval:
                                  description: The time duration between keep-alive
                                    probes.
                                  type: string
                                  x-kubernetes-validations:
                                  - message: must be a valid duration greater than
                                      1ms
                                    rule: duration(self) >= duration('1ms')
                                probes:
                                  description: Maximum number of keepalive probes
                                    to send without response before deciding the connection
                                    is dead.
                                  maximum: 4294967295
                                  minimum: 0
                                  type: integer
                                time:
                                  description: The time duration a connection needs
                                    to be idle before keep-alive probes start being
                                    sent.
                                  type: string
                                  x-kubernetes-validations:
                                  - message: must be a valid duration greater than
                                      1ms
                                    rule: duration(self) >= duration('1ms')
                              type: object
                          type: object
                      type: object
                    defaultEndpoint:
                      description: The IP endpoint or Unix domain socket to which
                        traffic should be forwarded to.
                      type: string
                    port:
                      description: The port associated with the listener.
                      properties:
                        name:
                          description: Label assigned to the port.
                          type: string
                        number:
                          description: A valid non-negative integer port number.
                          maximum: 4294967295
                          minimum: 0
                          type: integer
                        protocol:
                          description: The protocol exposed on the port.
                          type: string
                        targetPort:
                          maximum: 4294967295
                          minimum: 0
                          type: integer
                      type: object
                    tls:
                      description: Set of TLS related options that will enable TLS
                        termination on the sidecar for requests originating from outside
                        the mesh.
                      properties:
                        caCertificates:
                          description: REQUIRED if mode is `MUTUAL` or `OPTIONAL_MUTUAL`.
                          type: string
                        caCrl:
                          description: 'OPTIONAL: The path to the file containing
                            the certificate revocation list (CRL) to use in verifying
                            a presented client side certificate.'
                          type: string
                        cipherSuites:
                          description: 'Optional: If specified, only support the specified
                            cipher list.'
                          items:
                            type: string
                          type: array
                        credentialName:
                          description: For gateways running on Kubernetes, the name
                            of the secret that holds the TLS certs including the CA
                            certificates.
                          type: string
                        httpsRedirect:
                          description: If set to true, the load balancer will send
                            a 301 redirect for all http connections, asking the clients
                            to use HTTPS.
                          type: boolean
                        maxProtocolVersion:
                          description: |-
                            Optional: Maximum TLS protocol version.

                            Valid Options: TLS_AUTO, TLSV1_0, TLSV1_1, TLSV1_2, TLSV1_3
                          enum:
                          - TLS_AUTO
                          - TLSV1_0
                          - TLSV1_1
                          - TLSV1_2
                          - TLSV1_3
                          type: string
                        minProtocolVersion:
                          description: |-
                            Optional: Minimum TLS protocol version.

                            Valid Options: TLS_AUTO, TLSV1_0, TLSV1_1, TLSV1_2, TLSV1_3
                          enum:
                          - TLS_AUTO
                          - TLSV1_0
                          - TLSV1_1
                          - TLSV1_2
                          - TLSV1_3
                          type: string
                        mode:
                          description: |-
                            Optional: Indicates whether connections to this port should be secured using TLS.

                            Valid Options: PASSTHROUGH, SIMPLE, MUTUAL, AUTO_PASSTHROUGH, ISTIO_MUTUAL, OPTIONAL_MUTUAL
                          enum:
                          - PASSTHROUGH
                          - SIMPLE
                          - MUTUAL
                          - AUTO_PASSTHROUGH
                          - ISTIO_MUTUAL
                          - OPTIONAL_MUTUAL
                          type: string
                        privateKey:
                          description: REQUIRED if mode is `SIMPLE` or `MUTUAL`.
                          type: string
                        serverCertificate:
                          description: REQUIRED if mode is `SIMPLE` or `MUTUAL`.
                          type: string
                        subjectAltNames:
                          description: A list of alternate names to verify the subject
                            identity in the certificate presented by the client.
                          items:
                            type: string
                          type: array
                        verifyCertificateHash:
                          description: An optional list of hex-encoded SHA-256 hashes
                            of the authorized client certificates.
                          items:
                            type: string
                          type: array
                        verifyCertificateSpki:
                          description: An optional list of base64-encoded SHA-256
                            hashes of the SPKIs of authorized client certificates.
                          items:
                            type: string
                          type: array
                      type: object
                  required:
                  - port
                  type: object
                type: array
              outboundTrafficPolicy:
                description: Configuration for the outbound traffic policy.
                properties:
                  egressProxy:
                    properties:
                      host:
                        description: The name of a service from the service registry.
                        type: string
                      port:
                        description: Specifies the port on the host that is being
                          addressed.
                        properties:
                          number:
                            maximum: 4294967295
                            minimum: 0
                            type: integer
                        type: object
                      subset:
                        description: The name of a subset within the service.
                        type: string
                    required:
                    - host
                    type: object
                  mode:
                    description: |2-


                      Valid Options: REGISTRY_ONLY, ALLOW_ANY
                    enum:
                    - REGISTRY_ONLY
                    - ALLOW_ANY
                    type: string
                type: object
              workloadSelector:
                description: Criteria used to select the specific set of pods/VMs
                  on which this `Sidecar` configuration should be applied.
                properties:
                  labels:
                    additionalProperties:
                      type: string
                    description: One or more labels that indicate a specific set of
                      pods/VMs on which the configuration should be applied.
                    type: object
                type: object
            type: object
          status:
            type: object
            x-kubernetes-preserve-unknown-fields: true
        type: object
    served: true
    storage: false
    subresources:
      status: {}
  - name: v1alpha3
    schema:
      openAPIV3Schema:
        properties:
          spec:
            description: 'Configuration affecting network reachability of a sidecar.
              See more details at: https://istio.io/docs/reference/config/networking/sidecar.html'
            properties:
              egress:
                description: Egress specifies the configuration of the sidecar for
                  processing outbound traffic from the attached workload instance
                  to other services in the mesh.
                items:
                  properties:
                    bind:
                      description: The IP(IPv4 or IPv6) or the Unix domain socket
                        to which the listener should be bound to.
                      type: string
                    captureMode:
                      description: |-
                        When the bind address is an IP, the captureMode option dictates how traffic to the listener is expected to be captured (or not).

                        Valid Options: DEFAULT, IPTABLES, NONE
                      enum:
                      - DEFAULT
                      - IPTABLES
                      - NONE
                      type: string
                    hosts:
                      description: One or more service hosts exposed by the listener
                        in `namespace/dnsName` format.
                      items:
                        type: string
                      type: array
                    port:
                      description: The port associated with the listener.
                      properties:
                        name:
                          description: Label assigned to the port.
                          type: string
                        number:
                          description: A valid non-negative integer port number.
                          maximum: 4294967295
                          minimum: 0
                          type: integer
                        protocol:
                          description: The protocol exposed on the port.
                          type: string
                        targetPort:
                          maximum: 4294967295
                          minimum: 0
                          type: integer
                      type: object
                  required:
                  - hosts
                  type: object
                type: array
              inboundConnectionPool:
                description: Settings controlling the volume of connections Envoy
                  will accept from the network.
                properties:
                  http:
                    description: HTTP connection pool settings.
                    properties:
                      h2UpgradePolicy:
                        description: |-
                          Specify if http1.1 connection should be upgraded to http2 for the associated destination.

                          Valid Options: DEFAULT, DO_NOT_UPGRADE, UPGRADE
                        enum:
                        - DEFAULT
                        - DO_NOT_UPGRADE
                        - UPGRADE
                        type: string
                      http1MaxPendingRequests:
                        description: Maximum number of requests that will be queued
                          while waiting for a ready connection pool connection.
                        format: int32
                        type: integer
                      http2MaxRequests:
                        description: Maximum number of active requests to a destination.
                        format: int32
                        type: integer
                      idleTimeout:
                        description: The idle timeout for upstream connection pool
                          connections.
                        type: string
                        x-kubernetes-validations:
                        - message: must be a valid duration greater than 1ms
                          rule: duration(self) >= duration('1ms')
                      maxConcurrentStreams:
                        description: The maximum number of concurrent streams allowed
                          for a peer on one HTTP/2 connection.
                        format: int32
                        type: integer
                      maxRequestsPerConnection:
                        description: Maximum number of requests per connection to
                          a backend.
                        format: int32
                        type: integer
                      maxRetries:
                        description: Maximum number of retries that can be outstanding
                          to all hosts in a cluster at a given time.
                        format: int32
                        type: integer
                      useClientProtocol:
                        description: If set to true, client protocol will be preserved
                          while initiating connection to backend.
                        type: boolean
                    type: object
                  tcp:
                    description: Settings common to both HTTP and TCP upstream connections.
                    properties:
                      connectTimeout:
                        description: TCP connection timeout.
                        type: string
                        x-kubernetes-validations:
                        - message: must be a valid duration greater than 1ms
                          rule: duration(self) >= duration('1ms')
                      idleTimeout:
                        description: The idle timeout for TCP connections.
                        type: string
                        x-kubernetes-validations:
                        - message: must be a valid duration greater than 1ms
                          rule: duration(self) >= duration('1ms')
                      maxConnectionDuration:
                        description: The maximum duration of a connection.
                        type: string
                        x-kubernetes-validations:
                        - message: must be a valid duration greater than 1ms
                          rule: duration(self) >= duration('1ms')
                      maxConnections:
                        description: Maximum number of HTTP1 /TCP connections to a
                          destination host.
                        format: int32
                        type: integer
                      tcpKeepalive:
                        description: If set then set SO_KEEPALIVE on the socket to
                          enable TCP Keepalives.
                        properties:
                          interval:
                            description: The time duration between keep-alive probes.
                            type: string
                            x-kubernetes-validations:
                            - message: must be a valid duration greater than 1ms
                              rule: duration(self) >= duration('1ms')
                          probes:
                            description: Maximum number of keepalive probes to send
                              without response before deciding the connection is dead.
                            maximum: 4294967295
                            minimum: 0
                            type: integer
                          time:
                            description: The time duration a connection needs to be
                              idle before keep-alive probes start being sent.
                            type: string
                            x-kubernetes-validations:
                            - message: must be a valid duration greater than 1ms
                              rule: duration(self) >= duration('1ms')
                        type: object
                    type: object
                type: object
              ingress:
                description: Ingress specifies the configuration of the sidecar for
                  processing inbound traffic to the attached workload instance.
                items:
                  properties:
                    bind:
                      description: The IP(IPv4 or IPv6) to which the listener should
                        be bound.
                      type: string
                    captureMode:
                      description: |-
                        The captureMode option dictates how traffic to the listener is expected to be captured (or not).

                        Valid Options: DEFAULT, IPTABLES, NONE
                      enum:
                      - DEFAULT
                      - IPTABLES
                      - NONE
                      type: string
                    connectionPool:
                      description: Settings controlling the volume of connections
                        Envoy will accept from the network.
                      properties:
                        http:
                          description: HTTP connection pool settings.
                          properties:
                            h2UpgradePolicy:
                              description: |-
                                Specify if http1.1 connection should be upgraded to http2 for the associated destination.

                                Valid Options: DEFAULT, DO_NOT_UPGRADE, UPGRADE
                              enum:
                              - DEFAULT
                              - DO_NOT_UPGRADE
                              - UPGRADE
                              type: string
                            http1MaxPendingRequests:
                              description: Maximum number of requests that will be
                                queued while waiting for a ready connection pool connection.
                              format: int32
                              type: integer
                            http2MaxRequests:
                              description: Maximum number of active requests to a
                                destination.
                              format: int32
                              type: integer
                            idleTimeout:
                              description: The idle timeout for upstream connection
                                pool connections.
                              type: string
                              x-kubernetes-validations:
                              - message: must be a valid duration greater than 1ms
                                rule: duration(self) >= duration('1ms')
                            maxConcurrentStreams:
                              description: The maximum number of concurrent streams
                                allowed for a peer on one HTTP/2 connection.
                              format: int32
                              type: integer
                            maxRequestsPerConnection:
                              description: Maximum number of requests per connection
                                to a backend.
                              format: int32
                              type: integer
                            maxRetries:
                              description: Maximum number of retries that can be outstanding
                                to all hosts in a cluster at a given time.
                              format: int32
                              type: integer
                            useClientProtocol:
                              description: If set to true, client protocol will be
                                preserved while initiating connection to backend.
                              type: boolean
                          type: object
                        tcp:
                          description: Settings common to both HTTP and TCP upstream
                            connections.
                          properties:
                            connectTimeout:
                              description: TCP connection timeout.
                              type: string
                              x-kubernetes-validations:
                              - message: must be a valid duration greater than 1ms
                                rule: duration(self) >= duration('1ms')
                            idleTimeout:
                              description: The idle timeout for TCP connections.
                              type: string
                              x-kubernetes-validations:
                              - message: must be a valid duration greater than 1ms
                                rule: duration(self) >= duration('1ms')
                            maxConnectionDuration:
                              description: The maximum duration of a connection.
                              type: string
                              x-kubernetes-validations:
                              - message: must be a valid duration greater than 1ms
                                rule: duration(self) >= duration('1ms')
                            maxConnections:
                              description: Maximum number of HTTP1 /TCP connections
                                to a destination host.
                              format: int32
                              type: integer
                            tcpKeepalive:
                              description: If set then set SO_KEEPALIVE on the socket
                                to enable TCP Keepalives.
                              properties:
                                interval:
                                  description: The time duration between keep-alive
                                    probes.
                                  type: string
                                  x-kubernetes-validations:
                                  - message: must be a valid duration greater than
                                      1ms
                                    rule: duration(self) >= duration('1ms')
                                probes:
                                  description: Maximum number of keepalive probes
                                    to send without response before deciding the connection
                                    is dead.
                                  maximum: 4294967295
                                  minimum: 0
                                  type: integer
                                time:
                                  description: The time duration a connection needs
                                    to be idle before keep-alive probes start being
                                    sent.
                                  type: string
                                  x-kubernetes-validations:
                                  - message: must be a valid duration greater than
                                      1ms
                                    rule: duration(self) >= duration('1ms')
                              type: object
                          type: object
                      type: object
                    defaultEndpoint:
                      description: The IP endpoint or Unix domain socket to which
                        traffic should be forwarded to.
                      type: string
                    port:
                      description: The port associated with the listener.
                      properties:
                        name:
                          description: Label assigned to the port.
                          type: string
                        number:
                          description: A valid non-negative integer port number.
                          maximum: 4294967295
                          minimum: 0
                          type: integer
                        protocol:
                          description: The protocol exposed on the port.
                          type: string
                        targetPort:
                          maximum: 4294967295
                          minimum: 0
                          type: integer
                      type: object
                    tls:
                      description: Set of TLS related options that will enable TLS
                        termination on the sidecar for requests originating from outside
                        the mesh.
                      properties:
                        caCertificates:
                          description: REQUIRED if mode is `MUTUAL` or `OPTIONAL_MUTUAL`.
                          type: string
                        caCrl:
                          description: 'OPTIONAL: The path to the file containing
                            the certificate revocation list (CRL) to use in verifying
                            a presented client side certificate.'
                          type: string
                        cipherSuites:
                          description: 'Optional: If specified, only support the specified
                            cipher list.'
                          items:
                            type: string
                          type: array
                        credentialName:
                          description: For gateways running on Kubernetes, the name
                            of the secret that holds the TLS certs including the CA
                            certificates.
                          type: string
                        httpsRedirect:
                          description: If set to true, the load balancer will send
                            a 301 redirect for all http connections, asking the clients
                            to use HTTPS.
                          type: boolean
                        maxProtocolVersion:
                          description: |-
                            Optional: Maximum TLS protocol version.

                            Valid Options: TLS_AUTO, TLSV1_0, TLSV1_1, TLSV1_2, TLSV1_3
                          enum:
                          - TLS_AUTO
                          - TLSV1_0
                          - TLSV1_1
                          - TLSV1_2
                          - TLSV1_3
                          type: string
                        minProtocolVersion:
                          description: |-
                            Optional: Minimum TLS protocol version.

                            Valid Options: TLS_AUTO, TLSV1_0, TLSV1_1, TLSV1_2, TLSV1_3
                          enum:
                          - TLS_AUTO
                          - TLSV1_0
                          - TLSV1_1
                          - TLSV1_2
                          - TLSV1_3
                          type: string
                        mode:
                          description: |-
                            Optional: Indicates whether connections to this port should be secured using TLS.

                            Valid Options: PASSTHROUGH, SIMPLE, MUTUAL, AUTO_PASSTHROUGH, ISTIO_MUTUAL, OPTIONAL_MUTUAL
                          enum:
                          - PASSTHROUGH
                          - SIMPLE
                          - MUTUAL
                          - AUTO_PASSTHROUGH
                          - ISTIO_MUTUAL
                          - OPTIONAL_MUTUAL
                          type: string
                        privateKey:
                          description: REQUIRED if mode is `SIMPLE` or `MUTUAL`.
                          type: string
                        serverCertificate:
                          description: REQUIRED if mode is `SIMPLE` or `MUTUAL`.
                          type: string
                        subjectAltNames:
                          description: A list of alternate names to verify the subject
                            identity in the certificate presented by the client.
                          items:
                            type: string
                          type: array
                        verifyCertificateHash:
                          description: An optional list of hex-encoded SHA-256 hashes
                            of the authorized client certificates.
                          items:
                            type: string
                          type: array
                        verifyCertificateSpki:
                          description: An optional list of base64-encoded SHA-256
                            hashes of the SPKIs of authorized client certificates.
                          items:
                            type: string
                          type: array
                      type: object
                  required:
                  - port
                  type: object
                type: array
              outboundTrafficPolicy:
                description: Configuration for the outbound traffic policy.
                properties:
                  egressProxy:
                    properties:
                      host:
                        description: The name of a service from the service registry.
                        type: string
                      port:
                        description: Specifies the port on the host that is being
                          addressed.
                        properties:
                          number:
                            maximum: 4294967295
                            minimum: 0
                            type: integer
                        type: object
                      subset:
                        description: The name of a subset within the service.
                        type: string
                    required:
                    - host
                    type: object
                  mode:
                    description: |2-


                      Valid Options: REGISTRY_ONLY, ALLOW_ANY
                    enum:
                    - REGISTRY_ONLY
                    - ALLOW_ANY
                    type: string
                type: object
              workloadSelector:
                description: Criteria used to select the specific set of pods/VMs
                  on which this `Sidecar` configuration should be applied.
                properties:
                  labels:
                    additionalProperties:
                      type: string
                    description: One or more labels that indicate a specific set of
                      pods/VMs on which the configuration should be applied.
                    type: object
                type: object
            type: object
          status:
            type: object
            x-kubernetes-preserve-unknown-fields: true
        type: object
    served: true
    storage: false
    subresources:
      status: {}
  - name: v1beta1
    schema:
      openAPIV3Schema:
        properties:
          spec:
            description: 'Configuration affecting network reachability of a sidecar.
              See more details at: https://istio.io/docs/reference/config/networking/sidecar.html'
            properties:
              egress:
                description: Egress specifies the configuration of the sidecar for
                  processing outbound traffic from the attached workload instance
                  to other services in the mesh.
                items:
                  properties:
                    bind:
                      description: The IP(IPv4 or IPv6) or the Unix domain socket
                        to which the listener should be bound to.
                      type: string
                    captureMode:
                      description: |-
                        When the bind address is an IP, the captureMode option dictates how traffic to the listener is expected to be captured (or not).

                        Valid Options: DEFAULT, IPTABLES, NONE
                      enum:
                      - DEFAULT
                      - IPTABLES
                      - NONE
                      type: string
                    hosts:
                      description: One or more service hosts exposed by the listener
                        in `namespace/dnsName` format.
                      items:
                        type: string
                      type: array
                    port:
                      description: The port associated with the listener.
                      properties:
                        name:
                          description: Label assigned to the port.
                          type: string
                        number:
                          description: A valid non-negative integer port number.
                          maximum: 4294967295
                          minimum: 0
                          type: integer
                        protocol:
                          description: The protocol exposed on the port.
                          type: string
                        targetPort:
                          maximum: 4294967295
                          minimum: 0
                          type: integer
                      type: object
                  required:
                  - hosts
                  type: object
                type: array
              inboundConnectionPool:
                description: Settings controlling the volume of connections Envoy
                  will accept from the network.
                properties:
                  http:
                    description: HTTP connection pool settings.
                    properties:
                      h2UpgradePolicy:
                        description: |-
                          Specify if http1.1 connection should be upgraded to http2 for the associated destination.

                          Valid Options: DEFAULT, DO_NOT_UPGRADE, UPGRADE
                        enum:
                        - DEFAULT
                        - DO_NOT_UPGRADE
                        - UPGRADE
                        type: string
                      http1MaxPendingRequests:
                        description: Maximum number of requests that will be queued
                          while waiting for a ready connection pool connection.
                        format: int32
                        type: integer
                      http2MaxRequests:
                        description: Maximum number of active requests to a destination.
                        format: int32
                        type: integer
                      idleTimeout:
                        description: The idle timeout for upstream connection pool
                          connections.
                        type: string
                        x-kubernetes-validations:
                        - message: must be a valid duration greater than 1ms
                          rule: duration(self) >= duration('1ms')
                      maxConcurrentStreams:
                        description: The maximum number of concurrent streams allowed
                          for a peer on one HTTP/2 connection.
                        format: int32
                        type: integer
                      maxRequestsPerConnection:
                        description: Maximum number of requests per connection to
                          a backend.
                        format: int32
                        type: integer
                      maxRetries:
                        description: Maximum number of retries that can be outstanding
                          to all hosts in a cluster at a given time.
                        format: int32
                        type: integer
                      useClientProtocol:
                        description: If set to true, client protocol will be preserved
                          while initiating connection to backend.
                        type: boolean
                    type: object
                  tcp:
                    description: Settings common to both HTTP and TCP upstream connections.
                    properties:
                      connectTimeout:
                        description: TCP connection timeout.
                        type: string
                        x-kubernetes-validations:
                        - message: must be a valid duration greater than 1ms
                          rule: duration(self) >= duration('1ms')
                      idleTimeout:
                        description: The idle timeout for TCP connections.
                        type: string
                        x-kubernetes-validations:
                        - message: must be a valid duration greater than 1ms
                          rule: duration(self) >= duration('1ms')
                      maxConnectionDuration:
                        description: The maximum duration of a connection.
                        type: string
                        x-kubernetes-validations:
                        - message: must be a valid duration greater than 1ms
                          rule: duration(self) >= duration('1ms')
                      maxConnections:
                        description: Maximum number of HTTP1 /TCP connections to a
                          destination host.
                        format: int32
                        type: integer
                      tcpKeepalive:
                        description: If set then set SO_KEEPALIVE on the socket to
                          enable TCP Keepalives.
                        properties:
                          interval:
                            description: The time duration between keep-alive probes.
                            type: string
                            x-kubernetes-validations:
                            - message: must be a valid duration greater than 1ms
                              rule: duration(self) >= duration('1ms')
                          probes:
                            description: Maximum number of keepalive probes to send
                              without response before deciding the connection is dead.
                            maximum: 4294967295
                            minimum: 0
                            type: integer
                          time:
                            description: The time duration a connection needs to be
                              idle before keep-alive probes start being sent.
                            type: string
                            x-kubernetes-validations:
                            - message: must be a valid duration greater than 1ms
                              rule: duration(self) >= duration('1ms')
                        type: object
                    type: object
                type: object
              ingress:
                description: Ingress specifies the configuration of the sidecar for
                  processing inbound traffic to the attached workload instance.
                items:
                  properties:
                    bind:
                      description: The IP(IPv4 or IPv6) to which the listener should
                        be bound.
                      type: string
                    captureMode:
                      description: |-
                        The captureMode option dictates how traffic to the listener is expected to be captured (or not).

                        Valid Options: DEFAULT, IPTABLES, NONE
                      enum:
                      - DEFAULT
                      - IPTABLES
                      - NONE
                      type: string
                    connectionPool:
                      description: Settings controlling the volume of connections
                        Envoy will accept from the network.
                      properties:
                        http:
                          description: HTTP connection pool settings.
                          properties:
                            h2UpgradePolicy:
                              description: |-
                                Specify if http1.1 connection should be upgraded to http2 for the associated destination.

                                Valid Options: DEFAULT, DO_NOT_UPGRADE, UPGRADE
                              enum:
                              - DEFAULT
                              - DO_NOT_UPGRADE
                              - UPGRADE
                              type: string
                            http1MaxPendingRequests:
                              description: Maximum number of requests that will be
                                queued while waiting for a ready connection pool connection.
                              format: int32
                              type: integer
                            http2MaxRequests:
                              description: Maximum number of active requests to a
                                destination.
                              format: int32
                              type: integer
                            idleTimeout:
                              description: The idle timeout for upstream connection
                                pool connections.
                              type: string
                              x-kubernetes-validations:
                              - message: must be a valid duration greater than 1ms
                                rule: duration(self) >= duration('1ms')
                            maxConcurrentStreams:
                              description: The maximum number of concurrent streams
                                allowed for a peer on one HTTP/2 connection.
                              format: int32
                              type: integer
                            maxRequestsPerConnection:
                              description: Maximum number of requests per connection
                                to a backend.
                              format: int32
                              type: integer
                            maxRetries:
                              description: Maximum number of retries that can be outstanding
                                to all hosts in a cluster at a given time.
                              format: int32
                              type: integer
                            useClientProtocol:
                              description: If set to true, client protocol will be
                                preserved while initiating connection to backend.
                              type: boolean
                          type: object
                        tcp:
                          description: Settings common to both HTTP and TCP upstream
                            connections.
                          properties:
                            connectTimeout:
                              description: TCP connection timeout.
                              type: string
                              x-kubernetes-validations:
                              - message: must be a valid duration greater than 1ms
                                rule: duration(self) >= duration('1ms')
                            idleTimeout:
                              description: The idle timeout for TCP connections.
                              type: string
                              x-kubernetes-validations:
                              - message: must be a valid duration greater than 1ms
                                rule: duration(self) >= duration('1ms')
                            maxConnectionDuration:
                              description: The maximum duration of a connection.
                              type: string
                              x-kubernetes-validations:
                              - message: must be a valid duration greater than 1ms
                                rule: duration(self) >= duration('1ms')
                            maxConnections:
                              description: Maximum number of HTTP1 /TCP connections
                                to a destination host.
                              format: int32
                              type: integer
                            tcpKeepalive:
                              description: If set then set SO_KEEPALIVE on the socket
                                to enable TCP Keepalives.
                              properties:
                                interval:
                                  description: The time duration between keep-alive
                                    probes.
                                  type: string
                                  x-kubernetes-validations:
                                  - message: must be a valid duration greater than
                                      1ms
                                    rule: duration(self) >= duration('1ms')
                                probes:
                                  description: Maximum number of keepalive probes
                                    to send without response before deciding the connection
                                    is dead.
                                  maximum: 4294967295
                                  minimum: 0
                                  type: integer
                                time:
                                  description: The time duration a connection needs
                                    to be idle before keep-alive probes start being
                                    sent.
                                  type: string
                                  x-kubernetes-validations:
                                  - message: must be a valid duration greater than
                                      1ms
                                    rule: duration(self) >= duration('1ms')
                              type: object
                          type: object
                      type: object
                    defaultEndpoint:
                      description: The IP endpoint or Unix domain socket to which
                        traffic should be forwarded to.
                      type: string
                    port:
                      description: The port associated with the listener.
                      properties:
                        name:
                          description: Label assigned to the port.
                          type: string
                        number:
                          description: A valid non-negative integer port number.
                          maximum: 4294967295
                          minimum: 0
                          type: integer
                        protocol:
                          description: The protocol exposed on the port.
                          type: string
                        targetPort:
                          maximum: 4294967295
                          minimum: 0
                          type: integer
                      type: object
                    tls:
                      description: Set of TLS related options that will enable TLS
                        termination on the sidecar for requests originating from outside
                        the mesh.
                      properties:
                        caCertificates:
                          description: REQUIRED if mode is `MUTUAL` or `OPTIONAL_MUTUAL`.
                          type: string
                        caCrl:
                          description: 'OPTIONAL: The path to the file containing
                            the certificate revocation list (CRL) to use in verifying
                            a presented client side certificate.'
                          type: string
                        cipherSuites:
                          description: 'Optional: If specified, only support the specified
                            cipher list.'
                          items:
                            type: string
                          type: array
                        credentialName:
                          description: For gateways running on Kubernetes, the name
                            of the secret that holds the TLS certs including the CA
                            certificates.
                          type: string
                        httpsRedirect:
                          description: If set to true, the load balancer will send
                            a 301 redirect for all http connections, asking the clients
                            to use HTTPS.
                          type: boolean
                        maxProtocolVersion:
                          description: |-
                            Optional: Maximum TLS protocol version.

                            Valid Options: TLS_AUTO, TLSV1_0, TLSV1_1, TLSV1_2, TLSV1_3
                          enum:
                          - TLS_AUTO
                          - TLSV1_0
                          - TLSV1_1
                          - TLSV1_2
                          - TLSV1_3
                          type: string
                        minProtocolVersion:
                          description: |-
                            Optional: Minimum TLS protocol version.

                            Valid Options: TLS_AUTO, TLSV1_0, TLSV1_1, TLSV1_2, TLSV1_3
                          enum:
                          - TLS_AUTO
                          - TLSV1_0
                          - TLSV1_1
                          - TLSV1_2
                          - TLSV1_3
                          type: string
                        mode:
                          description: |-
                            Optional: Indicates whether connections to this port should be secured using TLS.

                            Valid Options: PASSTHROUGH, SIMPLE, MUTUAL, AUTO_PASSTHROUGH, ISTIO_MUTUAL, OPTIONAL_MUTUAL
                          enum:
                          - PASSTHROUGH
                          - SIMPLE
                          - MUTUAL
                          - AUTO_PASSTHROUGH
                          - ISTIO_MUTUAL
                          - OPTIONAL_MUTUAL
                          type: string
                        privateKey:
                          description: REQUIRED if mode is `SIMPLE` or `MUTUAL`.
                          type: string
                        serverCertificate:
                          description: REQUIRED if mode is `SIMPLE` or `MUTUAL`.
                          type: string
                        subjectAltNames:
                          description: A list of alternate names to verify the subject
                            identity in the certificate presented by the client.
                          items:
                            type: string
                          type: array
                        verifyCertificateHash:
                          description: An optional list of hex-encoded SHA-256 hashes
                            of the authorized client certificates.
                          items:
                            type: string
                          type: array
                        verifyCertificateSpki:
                          description: An optional list of base64-encoded SHA-256
                            hashes of the SPKIs of authorized client certificates.
                          items:
                            type: string
                          type: array
                      type: object
                  required:
                  - port
                  type: object
                type: array
              outboundTrafficPolicy:
                description: Configuration for the outbound traffic policy.
                properties:
                  egressProxy:
                    properties:
                      host:
                        description: The name of a service from the service registry.
                        type: string
                      port:
                        description: Specifies the port on the host that is being
                          addressed.
                        properties:
                          number:
                            maximum: 4294967295
                            minimum: 0
                            type: integer
                        type: object
                      subset:
                        description: The name of a subset within the service.
                        type: string
                    required:
                    - host
                    type: object
                  mode:
                    description: |2-


                      Valid Options: REGISTRY_ONLY, ALLOW_ANY
                    enum:
                    - REGISTRY_ONLY
                    - ALLOW_ANY
                    type: string
                type: object
              workloadSelector:
                description: Criteria used to select the specific set of pods/VMs
                  on which this `Sidecar` configuration should be applied.
                properties:
                  labels:
                    additionalProperties:
                      type: string
                    description: One or more labels that indicate a specific set of
                      pods/VMs on which the configuration should be applied.
                    type: object
                type: object
            type: object
          status:
            type: object
            x-kubernetes-preserve-unknown-fields: true
        type: object
    served: true
    storage: true
    subresources:
      status: {}
---
# Source: istio-microk8s/charts/base/templates/crds.yaml
apiVersion: apiextensions.k8s.io/v1
kind: CustomResourceDefinition
metadata:
  annotations:
    "helm.sh/resource-policy": keep
  labels:
    app: istio-pilot
    chart: istio
    heritage: Tiller
    release: istio
  name: virtualservices.networking.istio.io
spec:
  group: networking.istio.io
  names:
    categories:
    - istio-io
    - networking-istio-io
    kind: VirtualService
    listKind: VirtualServiceList
    plural: virtualservices
    shortNames:
    - vs
    singular: virtualservice
  scope: Namespaced
  versions:
  - additionalPrinterColumns:
    - description: The names of gateways and sidecars that should apply these routes
      jsonPath: .spec.gateways
      name: Gateways
      type: string
    - description: The destination hosts to which traffic is being sent
      jsonPath: .spec.hosts
      name: Hosts
      type: string
    - description: 'CreationTimestamp is a timestamp representing the server time
        when this object was created. It is not guaranteed to be set in happens-before
        order across separate operations. Clients may not set this value. It is represented
        in RFC3339 form and is in UTC. Populated by the system. Read-only. Null for
        lists. More info: https://git.k8s.io/community/contributors/devel/api-conventions.md#metadata'
      jsonPath: .metadata.creationTimestamp
      name: Age
      type: date
    name: v1
    schema:
      openAPIV3Schema:
        properties:
          spec:
            description: 'Configuration affecting label/content routing, sni routing,
              etc. See more details at: https://istio.io/docs/reference/config/networking/virtual-service.html'
            properties:
              exportTo:
                description: A list of namespaces to which this virtual service is
                  exported.
                items:
                  type: string
                type: array
              gateways:
                description: The names of gateways and sidecars that should apply
                  these routes.
                items:
                  type: string
                type: array
              hosts:
                description: The destination hosts to which traffic is being sent.
                items:
                  type: string
                type: array
              http:
                description: An ordered list of route rules for HTTP traffic.
                items:
                  properties:
                    corsPolicy:
                      description: Cross-Origin Resource Sharing policy (CORS).
                      properties:
                        allowCredentials:
                          description: Indicates whether the caller is allowed to
                            send the actual request (not the preflight) using credentials.
                          nullable: true
                          type: boolean
                        allowHeaders:
                          description: List of HTTP headers that can be used when
                            requesting the resource.
                          items:
                            type: string
                          type: array
                        allowMethods:
                          description: List of HTTP methods allowed to access the
                            resource.
                          items:
                            type: string
                          type: array
                        allowOrigin:
                          items:
                            type: string
                          type: array
                        allowOrigins:
                          description: String patterns that match allowed origins.
                          items:
                            oneOf:
                            - not:
                                anyOf:
                                - required:
                                  - exact
                                - required:
                                  - prefix
                                - required:
                                  - regex
                            - required:
                              - exact
                            - required:
                              - prefix
                            - required:
                              - regex
                            properties:
                              exact:
                                type: string
                              prefix:
                                type: string
                              regex:
                                description: '[RE2 style regex-based match](https://github.com/google/re2/wiki/Syntax).'
                                type: string
                            type: object
                          type: array
                        exposeHeaders:
                          description: A list of HTTP headers that the browsers are
                            allowed to access.
                          items:
                            type: string
                          type: array
                        maxAge:
                          description: Specifies how long the results of a preflight
                            request can be cached.
                          type: string
                          x-kubernetes-validations:
                          - message: must be a valid duration greater than 1ms
                            rule: duration(self) >= duration('1ms')
                        unmatchedPreflights:
                          description: |-
                            Indicates whether preflight requests not matching the configured allowed origin shouldn't be forwarded to the upstream.

                            Valid Options: FORWARD, IGNORE
                          enum:
                          - UNSPECIFIED
                          - FORWARD
                          - IGNORE
                          type: string
                      type: object
                    delegate:
                      description: Delegate is used to specify the particular VirtualService
                        which can be used to define delegate HTTPRoute.
                      properties:
                        name:
                          description: Name specifies the name of the delegate VirtualService.
                          type: string
                        namespace:
                          description: Namespace specifies the namespace where the
                            delegate VirtualService resides.
                          type: string
                      type: object
                    directResponse:
                      description: A HTTP rule can either return a direct_response,
                        redirect or forward (default) traffic.
                      properties:
                        body:
                          description: Specifies the content of the response body.
                          oneOf:
                          - not:
                              anyOf:
                              - required:
                                - string
                              - required:
                                - bytes
                          - required:
                            - string
                          - required:
                            - bytes
                          properties:
                            bytes:
                              description: response body as base64 encoded bytes.
                              format: binary
                              type: string
                            string:
                              type: string
                          type: object
                        status:
                          description: Specifies the HTTP response status to be returned.
                          maximum: 4294967295
                          minimum: 0
                          type: integer
                      required:
                      - status
                      type: object
                    fault:
                      description: Fault injection policy to apply on HTTP traffic
                        at the client side.
                      properties:
                        abort:
                          description: Abort Http request attempts and return error
                            codes back to downstream service, giving the impression
                            that the upstream service is faulty.
                          oneOf:
                          - not:
                              anyOf:
                              - required:
                                - httpStatus
                              - required:
                                - grpcStatus
                              - required:
                                - http2Error
                          - required:
                            - httpStatus
                          - required:
                            - grpcStatus
                          - required:
                            - http2Error
                          properties:
                            grpcStatus:
                              description: GRPC status code to use to abort the request.
                              type: string
                            http2Error:
                              type: string
                            httpStatus:
                              description: HTTP status code to use to abort the Http
                                request.
                              format: int32
                              type: integer
                            percentage:
                              description: Percentage of requests to be aborted with
                                the error code provided.
                              properties:
                                value:
                                  format: double
                                  type: number
                              type: object
                          type: object
                        delay:
                          description: Delay requests before forwarding, emulating
                            various failures such as network issues, overloaded upstream
                            service, etc.
                          oneOf:
                          - not:
                              anyOf:
                              - required:
                                - fixedDelay
                              - required:
                                - exponentialDelay
                          - required:
                            - fixedDelay
                          - required:
                            - exponentialDelay
                          properties:
                            exponentialDelay:
                              type: string
                              x-kubernetes-validations:
                              - message: must be a valid duration greater than 1ms
                                rule: duration(self) >= duration('1ms')
                            fixedDelay:
                              description: Add a fixed delay before forwarding the
                                request.
                              type: string
                              x-kubernetes-validations:
                              - message: must be a valid duration greater than 1ms
                                rule: duration(self) >= duration('1ms')
                            percent:
                              description: Percentage of requests on which the delay
                                will be injected (0-100).
                              format: int32
                              type: integer
                            percentage:
                              description: Percentage of requests on which the delay
                                will be injected.
                              properties:
                                value:
                                  format: double
                                  type: number
                              type: object
                          type: object
                      type: object
                    headers:
                      properties:
                        request:
                          properties:
                            add:
                              additionalProperties:
                                type: string
                              type: object
                            remove:
                              items:
                                type: string
                              type: array
                            set:
                              additionalProperties:
                                type: string
                              type: object
                          type: object
                        response:
                          properties:
                            add:
                              additionalProperties:
                                type: string
                              type: object
                            remove:
                              items:
                                type: string
                              type: array
                            set:
                              additionalProperties:
                                type: string
                              type: object
                          type: object
                      type: object
                    match:
                      description: Match conditions to be satisfied for the rule to
                        be activated.
                      items:
                        properties:
                          authority:
                            description: 'HTTP Authority values are case-sensitive
                              and formatted as follows: - `exact: "value"` for exact
                              string match - `prefix: "value"` for prefix-based match
                              - `regex: "value"` for [RE2 style regex-based match](https://github.com/google/re2/wiki/Syntax).'
                            oneOf:
                            - not:
                                anyOf:
                                - required:
                                  - exact
                                - required:
                                  - prefix
                                - required:
                                  - regex
                            - required:
                              - exact
                            - required:
                              - prefix
                            - required:
                              - regex
                            properties:
                              exact:
                                type: string
                              prefix:
                                type: string
                              regex:
                                description: '[RE2 style regex-based match](https://github.com/google/re2/wiki/Syntax).'
                                type: string
                            type: object
                          gateways:
                            description: Names of gateways where the rule should be
                              applied.
                            items:
                              type: string
                            type: array
                          headers:
                            additionalProperties:
                              oneOf:
                              - not:
                                  anyOf:
                                  - required:
                                    - exact
                                  - required:
                                    - prefix
                                  - required:
                                    - regex
                              - required:
                                - exact
                              - required:
                                - prefix
                              - required:
                                - regex
                              properties:
                                exact:
                                  type: string
                                prefix:
                                  type: string
                                regex:
                                  description: '[RE2 style regex-based match](https://github.com/google/re2/wiki/Syntax).'
                                  type: string
                              type: object
                            description: The header keys must be lowercase and use
                              hyphen as the separator, e.g.
                            type: object
                          ignoreUriCase:
                            description: Flag to specify whether the URI matching
                              should be case-insensitive.
                            type: boolean
                          method:
                            description: 'HTTP Method values are case-sensitive and
                              formatted as follows: - `exact: "value"` for exact string
                              match - `prefix: "value"` for prefix-based match - `regex:
                              "value"` for [RE2 style regex-based match](https://github.com/google/re2/wiki/Syntax).'
                            oneOf:
                            - not:
                                anyOf:
                                - required:
                                  - exact
                                - required:
                                  - prefix
                                - required:
                                  - regex
                            - required:
                              - exact
                            - required:
                              - prefix
                            - required:
                              - regex
                            properties:
                              exact:
                                type: string
                              prefix:
                                type: string
                              regex:
                                description: '[RE2 style regex-based match](https://github.com/google/re2/wiki/Syntax).'
                                type: string
                            type: object
                          name:
                            description: The name assigned to a match.
                            type: string
                          port:
                            description: Specifies the ports on the host that is being
                              addressed.
                            maximum: 4294967295
                            minimum: 0
                            type: integer
                          queryParams:
                            additionalProperties:
                              oneOf:
                              - not:
                                  anyOf:
                                  - required:
                                    - exact
                                  - required:
                                    - prefix
                                  - required:
                                    - regex
                              - required:
                                - exact
                              - required:
                                - prefix
                              - required:
                                - regex
                              properties:
                                exact:
                                  type: string
                                prefix:
                                  type: string
                                regex:
                                  description: '[RE2 style regex-based match](https://github.com/google/re2/wiki/Syntax).'
                                  type: string
                              type: object
                            description: Query parameters for matching.
                            type: object
                          scheme:
                            description: 'URI Scheme values are case-sensitive and
                              formatted as follows: - `exact: "value"` for exact string
                              match - `prefix: "value"` for prefix-based match - `regex:
                              "value"` for [RE2 style regex-based match](https://github.com/google/re2/wiki/Syntax).'
                            oneOf:
                            - not:
                                anyOf:
                                - required:
                                  - exact
                                - required:
                                  - prefix
                                - required:
                                  - regex
                            - required:
                              - exact
                            - required:
                              - prefix
                            - required:
                              - regex
                            properties:
                              exact:
                                type: string
                              prefix:
                                type: string
                              regex:
                                description: '[RE2 style regex-based match](https://github.com/google/re2/wiki/Syntax).'
                                type: string
                            type: object
                          sourceLabels:
                            additionalProperties:
                              type: string
                            description: One or more labels that constrain the applicability
                              of a rule to source (client) workloads with the given
                              labels.
                            type: object
                          sourceNamespace:
                            description: Source namespace constraining the applicability
                              of a rule to workloads in that namespace.
                            type: string
                          statPrefix:
                            description: The human readable prefix to use when emitting
                              statistics for this route.
                            type: string
                          uri:
                            description: 'URI to match values are case-sensitive and
                              formatted as follows: - `exact: "value"` for exact string
                              match - `prefix: "value"` for prefix-based match - `regex:
                              "value"` for [RE2 style regex-based match](https://github.com/google/re2/wiki/Syntax).'
                            oneOf:
                            - not:
                                anyOf:
                                - required:
                                  - exact
                                - required:
                                  - prefix
                                - required:
                                  - regex
                            - required:
                              - exact
                            - required:
                              - prefix
                            - required:
                              - regex
                            properties:
                              exact:
                                type: string
                              prefix:
                                type: string
                              regex:
                                description: '[RE2 style regex-based match](https://github.com/google/re2/wiki/Syntax).'
                                type: string
                            type: object
                          withoutHeaders:
                            additionalProperties:
                              oneOf:
                              - not:
                                  anyOf:
                                  - required:
                                    - exact
                                  - required:
                                    - prefix
                                  - required:
                                    - regex
                              - required:
                                - exact
                              - required:
                                - prefix
                              - required:
                                - regex
                              properties:
                                exact:
                                  type: string
                                prefix:
                                  type: string
                                regex:
                                  description: '[RE2 style regex-based match](https://github.com/google/re2/wiki/Syntax).'
                                  type: string
                              type: object
                            description: withoutHeader has the same syntax with the
                              header, but has opposite meaning.
                            type: object
                        type: object
                      type: array
                    mirror:
                      description: Mirror HTTP traffic to a another destination in
                        addition to forwarding the requests to the intended destination.
                      properties:
                        host:
                          description: The name of a service from the service registry.
                          type: string
                        port:
                          description: Specifies the port on the host that is being
                            addressed.
                          properties:
                            number:
                              maximum: 4294967295
                              minimum: 0
                              type: integer
                          type: object
                        subset:
                          description: The name of a subset within the service.
                          type: string
                      required:
                      - host
                      type: object
                    mirror_percent:
                      maximum: 4294967295
                      minimum: 0
                      nullable: true
                      type: integer
                    mirrorPercent:
                      maximum: 4294967295
                      minimum: 0
                      nullable: true
                      type: integer
                    mirrorPercentage:
                      description: Percentage of the traffic to be mirrored by the
                        `mirror` field.
                      properties:
                        value:
                          format: double
                          type: number
                      type: object
                    mirrors:
                      description: Specifies the destinations to mirror HTTP traffic
                        in addition to the original destination.
                      items:
                        properties:
                          destination:
                            description: Destination specifies the target of the mirror
                              operation.
                            properties:
                              host:
                                description: The name of a service from the service
                                  registry.
                                type: string
                              port:
                                description: Specifies the port on the host that is
                                  being addressed.
                                properties:
                                  number:
                                    maximum: 4294967295
                                    minimum: 0
                                    type: integer
                                type: object
                              subset:
                                description: The name of a subset within the service.
                                type: string
                            required:
                            - host
                            type: object
                          percentage:
                            description: Percentage of the traffic to be mirrored
                              by the `destination` field.
                            properties:
                              value:
                                format: double
                                type: number
                            type: object
                        required:
                        - destination
                        type: object
                      type: array
                    name:
                      description: The name assigned to the route for debugging purposes.
                      type: string
                    redirect:
                      description: A HTTP rule can either return a direct_response,
                        redirect or forward (default) traffic.
                      oneOf:
                      - not:
                          anyOf:
                          - required:
                            - port
                          - required:
                            - derivePort
                      - required:
                        - port
                      - required:
                        - derivePort
                      properties:
                        authority:
                          description: On a redirect, overwrite the Authority/Host
                            portion of the URL with this value.
                          type: string
                        derivePort:
                          description: |-
                            On a redirect, dynamically set the port: * FROM_PROTOCOL_DEFAULT: automatically set to 80 for HTTP and 443 for HTTPS.

                            Valid Options: FROM_PROTOCOL_DEFAULT, FROM_REQUEST_PORT
                          enum:
                          - FROM_PROTOCOL_DEFAULT
                          - FROM_REQUEST_PORT
                          type: string
                        port:
                          description: On a redirect, overwrite the port portion of
                            the URL with this value.
                          maximum: 4294967295
                          minimum: 0
                          type: integer
                        redirectCode:
                          description: On a redirect, Specifies the HTTP status code
                            to use in the redirect response.
                          maximum: 4294967295
                          minimum: 0
                          type: integer
                        scheme:
                          description: On a redirect, overwrite the scheme portion
                            of the URL with this value.
                          type: string
                        uri:
                          description: On a redirect, overwrite the Path portion of
                            the URL with this value.
                          type: string
                      type: object
                    retries:
                      description: Retry policy for HTTP requests.
                      properties:
                        attempts:
                          description: Number of retries to be allowed for a given
                            request.
                          format: int32
                          type: integer
                        perTryTimeout:
                          description: Timeout per attempt for a given request, including
                            the initial call and any retries.
                          type: string
                          x-kubernetes-validations:
                          - message: must be a valid duration greater than 1ms
                            rule: duration(self) >= duration('1ms')
                        retryOn:
                          description: Specifies the conditions under which retry
                            takes place.
                          type: string
                        retryRemoteLocalities:
                          description: Flag to specify whether the retries should
                            retry to other localities.
                          nullable: true
                          type: boolean
                      type: object
                    rewrite:
                      description: Rewrite HTTP URIs and Authority headers.
                      properties:
                        authority:
                          description: rewrite the Authority/Host header with this
                            value.
                          type: string
                        uri:
                          description: rewrite the path (or the prefix) portion of
                            the URI with this value.
                          type: string
                        uriRegexRewrite:
                          description: rewrite the path portion of the URI with the
                            specified regex.
                          properties:
                            match:
                              description: '[RE2 style regex-based match](https://github.com/google/re2/wiki/Syntax).'
                              type: string
                            rewrite:
                              description: The string that should replace into matching
                                portions of original URI.
                              type: string
                          type: object
                      type: object
                    route:
                      description: A HTTP rule can either return a direct_response,
                        redirect or forward (default) traffic.
                      items:
                        properties:
                          destination:
                            description: Destination uniquely identifies the instances
                              of a service to which the request/connection should
                              be forwarded to.
                            properties:
                              host:
                                description: The name of a service from the service
                                  registry.
                                type: string
                              port:
                                description: Specifies the port on the host that is
                                  being addressed.
                                properties:
                                  number:
                                    maximum: 4294967295
                                    minimum: 0
                                    type: integer
                                type: object
                              subset:
                                description: The name of a subset within the service.
                                type: string
                            required:
                            - host
                            type: object
                          headers:
                            properties:
                              request:
                                properties:
                                  add:
                                    additionalProperties:
                                      type: string
                                    type: object
                                  remove:
                                    items:
                                      type: string
                                    type: array
                                  set:
                                    additionalProperties:
                                      type: string
                                    type: object
                                type: object
                              response:
                                properties:
                                  add:
                                    additionalProperties:
                                      type: string
                                    type: object
                                  remove:
                                    items:
                                      type: string
                                    type: array
                                  set:
                                    additionalProperties:
                                      type: string
                                    type: object
                                type: object
                            type: object
                          weight:
                            description: Weight specifies the relative proportion
                              of traffic to be forwarded to the destination.
                            format: int32
                            type: integer
                        required:
                        - destination
                        type: object
                      type: array
                    timeout:
                      description: Timeout for HTTP requests, default is disabled.
                      type: string
                      x-kubernetes-validations:
                      - message: must be a valid duration greater than 1ms
                        rule: duration(self) >= duration('1ms')
                  type: object
                type: array
              tcp:
                description: An ordered list of route rules for opaque TCP traffic.
                items:
                  properties:
                    match:
                      description: Match conditions to be satisfied for the rule to
                        be activated.
                      items:
                        properties:
                          destinationSubnets:
                            description: IPv4 or IPv6 ip addresses of destination
                              with optional subnet.
                            items:
                              type: string
                            type: array
                          gateways:
                            description: Names of gateways where the rule should be
                              applied.
                            items:
                              type: string
                            type: array
                          port:
                            description: Specifies the port on the host that is being
                              addressed.
                            maximum: 4294967295
                            minimum: 0
                            type: integer
                          sourceLabels:
                            additionalProperties:
                              type: string
                            description: One or more labels that constrain the applicability
                              of a rule to workloads with the given labels.
                            type: object
                          sourceNamespace:
                            description: Source namespace constraining the applicability
                              of a rule to workloads in that namespace.
                            type: string
                          sourceSubnet:
                            type: string
                        type: object
                      type: array
                    route:
                      description: The destination to which the connection should
                        be forwarded to.
                      items:
                        properties:
                          destination:
                            description: Destination uniquely identifies the instances
                              of a service to which the request/connection should
                              be forwarded to.
                            properties:
                              host:
                                description: The name of a service from the service
                                  registry.
                                type: string
                              port:
                                description: Specifies the port on the host that is
                                  being addressed.
                                properties:
                                  number:
                                    maximum: 4294967295
                                    minimum: 0
                                    type: integer
                                type: object
                              subset:
                                description: The name of a subset within the service.
                                type: string
                            required:
                            - host
                            type: object
                          weight:
                            description: Weight specifies the relative proportion
                              of traffic to be forwarded to the destination.
                            format: int32
                            type: integer
                        required:
                        - destination
                        type: object
                      type: array
                  type: object
                type: array
              tls:
                description: An ordered list of route rule for non-terminated TLS
                  & HTTPS traffic.
                items:
                  properties:
                    match:
                      description: Match conditions to be satisfied for the rule to
                        be activated.
                      items:
                        properties:
                          destinationSubnets:
                            description: IPv4 or IPv6 ip addresses of destination
                              with optional subnet.
                            items:
                              type: string
                            type: array
                          gateways:
                            description: Names of gateways where the rule should be
                              applied.
                            items:
                              type: string
                            type: array
                          port:
                            description: Specifies the port on the host that is being
                              addressed.
                            maximum: 4294967295
                            minimum: 0
                            type: integer
                          sniHosts:
                            description: SNI (server name indicator) to match on.
                            items:
                              type: string
                            type: array
                          sourceLabels:
                            additionalProperties:
                              type: string
                            description: One or more labels that constrain the applicability
                              of a rule to workloads with the given labels.
                            type: object
                          sourceNamespace:
                            description: Source namespace constraining the applicability
                              of a rule to workloads in that namespace.
                            type: string
                        required:
                        - sniHosts
                        type: object
                      type: array
                    route:
                      description: The destination to which the connection should
                        be forwarded to.
                      items:
                        properties:
                          destination:
                            description: Destination uniquely identifies the instances
                              of a service to which the request/connection should
                              be forwarded to.
                            properties:
                              host:
                                description: The name of a service from the service
                                  registry.
                                type: string
                              port:
                                description: Specifies the port on the host that is
                                  being addressed.
                                properties:
                                  number:
                                    maximum: 4294967295
                                    minimum: 0
                                    type: integer
                                type: object
                              subset:
                                description: The name of a subset within the service.
                                type: string
                            required:
                            - host
                            type: object
                          weight:
                            description: Weight specifies the relative proportion
                              of traffic to be forwarded to the destination.
                            format: int32
                            type: integer
                        required:
                        - destination
                        type: object
                      type: array
                  required:
                  - match
                  type: object
                type: array
            type: object
          status:
            type: object
            x-kubernetes-preserve-unknown-fields: true
        type: object
    served: true
    storage: false
    subresources:
      status: {}
  - additionalPrinterColumns:
    - description: The names of gateways and sidecars that should apply these routes
      jsonPath: .spec.gateways
      name: Gateways
      type: string
    - description: The destination hosts to which traffic is being sent
      jsonPath: .spec.hosts
      name: Hosts
      type: string
    - description: 'CreationTimestamp is a timestamp representing the server time
        when this object was created. It is not guaranteed to be set in happens-before
        order across separate operations. Clients may not set this value. It is represented
        in RFC3339 form and is in UTC. Populated by the system. Read-only. Null for
        lists. More info: https://git.k8s.io/community/contributors/devel/api-conventions.md#metadata'
      jsonPath: .metadata.creationTimestamp
      name: Age
      type: date
    name: v1alpha3
    schema:
      openAPIV3Schema:
        properties:
          spec:
            description: 'Configuration affecting label/content routing, sni routing,
              etc. See more details at: https://istio.io/docs/reference/config/networking/virtual-service.html'
            properties:
              exportTo:
                description: A list of namespaces to which this virtual service is
                  exported.
                items:
                  type: string
                type: array
              gateways:
                description: The names of gateways and sidecars that should apply
                  these routes.
                items:
                  type: string
                type: array
              hosts:
                description: The destination hosts to which traffic is being sent.
                items:
                  type: string
                type: array
              http:
                description: An ordered list of route rules for HTTP traffic.
                items:
                  properties:
                    corsPolicy:
                      description: Cross-Origin Resource Sharing policy (CORS).
                      properties:
                        allowCredentials:
                          description: Indicates whether the caller is allowed to
                            send the actual request (not the preflight) using credentials.
                          nullable: true
                          type: boolean
                        allowHeaders:
                          description: List of HTTP headers that can be used when
                            requesting the resource.
                          items:
                            type: string
                          type: array
                        allowMethods:
                          description: List of HTTP methods allowed to access the
                            resource.
                          items:
                            type: string
                          type: array
                        allowOrigin:
                          items:
                            type: string
                          type: array
                        allowOrigins:
                          description: String patterns that match allowed origins.
                          items:
                            oneOf:
                            - not:
                                anyOf:
                                - required:
                                  - exact
                                - required:
                                  - prefix
                                - required:
                                  - regex
                            - required:
                              - exact
                            - required:
                              - prefix
                            - required:
                              - regex
                            properties:
                              exact:
                                type: string
                              prefix:
                                type: string
                              regex:
                                description: '[RE2 style regex-based match](https://github.com/google/re2/wiki/Syntax).'
                                type: string
                            type: object
                          type: array
                        exposeHeaders:
                          description: A list of HTTP headers that the browsers are
                            allowed to access.
                          items:
                            type: string
                          type: array
                        maxAge:
                          description: Specifies how long the results of a preflight
                            request can be cached.
                          type: string
                          x-kubernetes-validations:
                          - message: must be a valid duration greater than 1ms
                            rule: duration(self) >= duration('1ms')
                        unmatchedPreflights:
                          description: |-
                            Indicates whether preflight requests not matching the configured allowed origin shouldn't be forwarded to the upstream.

                            Valid Options: FORWARD, IGNORE
                          enum:
                          - UNSPECIFIED
                          - FORWARD
                          - IGNORE
                          type: string
                      type: object
                    delegate:
                      description: Delegate is used to specify the particular VirtualService
                        which can be used to define delegate HTTPRoute.
                      properties:
                        name:
                          description: Name specifies the name of the delegate VirtualService.
                          type: string
                        namespace:
                          description: Namespace specifies the namespace where the
                            delegate VirtualService resides.
                          type: string
                      type: object
                    directResponse:
                      description: A HTTP rule can either return a direct_response,
                        redirect or forward (default) traffic.
                      properties:
                        body:
                          description: Specifies the content of the response body.
                          oneOf:
                          - not:
                              anyOf:
                              - required:
                                - string
                              - required:
                                - bytes
                          - required:
                            - string
                          - required:
                            - bytes
                          properties:
                            bytes:
                              description: response body as base64 encoded bytes.
                              format: binary
                              type: string
                            string:
                              type: string
                          type: object
                        status:
                          description: Specifies the HTTP response status to be returned.
                          maximum: 4294967295
                          minimum: 0
                          type: integer
                      required:
                      - status
                      type: object
                    fault:
                      description: Fault injection policy to apply on HTTP traffic
                        at the client side.
                      properties:
                        abort:
                          description: Abort Http request attempts and return error
                            codes back to downstream service, giving the impression
                            that the upstream service is faulty.
                          oneOf:
                          - not:
                              anyOf:
                              - required:
                                - httpStatus
                              - required:
                                - grpcStatus
                              - required:
                                - http2Error
                          - required:
                            - httpStatus
                          - required:
                            - grpcStatus
                          - required:
                            - http2Error
                          properties:
                            grpcStatus:
                              description: GRPC status code to use to abort the request.
                              type: string
                            http2Error:
                              type: string
                            httpStatus:
                              description: HTTP status code to use to abort the Http
                                request.
                              format: int32
                              type: integer
                            percentage:
                              description: Percentage of requests to be aborted with
                                the error code provided.
                              properties:
                                value:
                                  format: double
                                  type: number
                              type: object
                          type: object
                        delay:
                          description: Delay requests before forwarding, emulating
                            various failures such as network issues, overloaded upstream
                            service, etc.
                          oneOf:
                          - not:
                              anyOf:
                              - required:
                                - fixedDelay
                              - required:
                                - exponentialDelay
                          - required:
                            - fixedDelay
                          - required:
                            - exponentialDelay
                          properties:
                            exponentialDelay:
                              type: string
                              x-kubernetes-validations:
                              - message: must be a valid duration greater than 1ms
                                rule: duration(self) >= duration('1ms')
                            fixedDelay:
                              description: Add a fixed delay before forwarding the
                                request.
                              type: string
                              x-kubernetes-validations:
                              - message: must be a valid duration greater than 1ms
                                rule: duration(self) >= duration('1ms')
                            percent:
                              description: Percentage of requests on which the delay
                                will be injected (0-100).
                              format: int32
                              type: integer
                            percentage:
                              description: Percentage of requests on which the delay
                                will be injected.
                              properties:
                                value:
                                  format: double
                                  type: number
                              type: object
                          type: object
                      type: object
                    headers:
                      properties:
                        request:
                          properties:
                            add:
                              additionalProperties:
                                type: string
                              type: object
                            remove:
                              items:
                                type: string
                              type: array
                            set:
                              additionalProperties:
                                type: string
                              type: object
                          type: object
                        response:
                          properties:
                            add:
                              additionalProperties:
                                type: string
                              type: object
                            remove:
                              items:
                                type: string
                              type: array
                            set:
                              additionalProperties:
                                type: string
                              type: object
                          type: object
                      type: object
                    match:
                      description: Match conditions to be satisfied for the rule to
                        be activated.
                      items:
                        properties:
                          authority:
                            description: 'HTTP Authority values are case-sensitive
                              and formatted as follows: - `exact: "value"` for exact
                              string match - `prefix: "value"` for prefix-based match
                              - `regex: "value"` for [RE2 style regex-based match](https://github.com/google/re2/wiki/Syntax).'
                            oneOf:
                            - not:
                                anyOf:
                                - required:
                                  - exact
                                - required:
                                  - prefix
                                - required:
                                  - regex
                            - required:
                              - exact
                            - required:
                              - prefix
                            - required:
                              - regex
                            properties:
                              exact:
                                type: string
                              prefix:
                                type: string
                              regex:
                                description: '[RE2 style regex-based match](https://github.com/google/re2/wiki/Syntax).'
                                type: string
                            type: object
                          gateways:
                            description: Names of gateways where the rule should be
                              applied.
                            items:
                              type: string
                            type: array
                          headers:
                            additionalProperties:
                              oneOf:
                              - not:
                                  anyOf:
                                  - required:
                                    - exact
                                  - required:
                                    - prefix
                                  - required:
                                    - regex
                              - required:
                                - exact
                              - required:
                                - prefix
                              - required:
                                - regex
                              properties:
                                exact:
                                  type: string
                                prefix:
                                  type: string
                                regex:
                                  description: '[RE2 style regex-based match](https://github.com/google/re2/wiki/Syntax).'
                                  type: string
                              type: object
                            description: The header keys must be lowercase and use
                              hyphen as the separator, e.g.
                            type: object
                          ignoreUriCase:
                            description: Flag to specify whether the URI matching
                              should be case-insensitive.
                            type: boolean
                          method:
                            description: 'HTTP Method values are case-sensitive and
                              formatted as follows: - `exact: "value"` for exact string
                              match - `prefix: "value"` for prefix-based match - `regex:
                              "value"` for [RE2 style regex-based match](https://github.com/google/re2/wiki/Syntax).'
                            oneOf:
                            - not:
                                anyOf:
                                - required:
                                  - exact
                                - required:
                                  - prefix
                                - required:
                                  - regex
                            - required:
                              - exact
                            - required:
                              - prefix
                            - required:
                              - regex
                            properties:
                              exact:
                                type: string
                              prefix:
                                type: string
                              regex:
                                description: '[RE2 style regex-based match](https://github.com/google/re2/wiki/Syntax).'
                                type: string
                            type: object
                          name:
                            description: The name assigned to a match.
                            type: string
                          port:
                            description: Specifies the ports on the host that is being
                              addressed.
                            maximum: 4294967295
                            minimum: 0
                            type: integer
                          queryParams:
                            additionalProperties:
                              oneOf:
                              - not:
                                  anyOf:
                                  - required:
                                    - exact
                                  - required:
                                    - prefix
                                  - required:
                                    - regex
                              - required:
                                - exact
                              - required:
                                - prefix
                              - required:
                                - regex
                              properties:
                                exact:
                                  type: string
                                prefix:
                                  type: string
                                regex:
                                  description: '[RE2 style regex-based match](https://github.com/google/re2/wiki/Syntax).'
                                  type: string
                              type: object
                            description: Query parameters for matching.
                            type: object
                          scheme:
                            description: 'URI Scheme values are case-sensitive and
                              formatted as follows: - `exact: "value"` for exact string
                              match - `prefix: "value"` for prefix-based match - `regex:
                              "value"` for [RE2 style regex-based match](https://github.com/google/re2/wiki/Syntax).'
                            oneOf:
                            - not:
                                anyOf:
                                - required:
                                  - exact
                                - required:
                                  - prefix
                                - required:
                                  - regex
                            - required:
                              - exact
                            - required:
                              - prefix
                            - required:
                              - regex
                            properties:
                              exact:
                                type: string
                              prefix:
                                type: string
                              regex:
                                description: '[RE2 style regex-based match](https://github.com/google/re2/wiki/Syntax).'
                                type: string
                            type: object
                          sourceLabels:
                            additionalProperties:
                              type: string
                            description: One or more labels that constrain the applicability
                              of a rule to source (client) workloads with the given
                              labels.
                            type: object
                          sourceNamespace:
                            description: Source namespace constraining the applicability
                              of a rule to workloads in that namespace.
                            type: string
                          statPrefix:
                            description: The human readable prefix to use when emitting
                              statistics for this route.
                            type: string
                          uri:
                            description: 'URI to match values are case-sensitive and
                              formatted as follows: - `exact: "value"` for exact string
                              match - `prefix: "value"` for prefix-based match - `regex:
                              "value"` for [RE2 style regex-based match](https://github.com/google/re2/wiki/Syntax).'
                            oneOf:
                            - not:
                                anyOf:
                                - required:
                                  - exact
                                - required:
                                  - prefix
                                - required:
                                  - regex
                            - required:
                              - exact
                            - required:
                              - prefix
                            - required:
                              - regex
                            properties:
                              exact:
                                type: string
                              prefix:
                                type: string
                              regex:
                                description: '[RE2 style regex-based match](https://github.com/google/re2/wiki/Syntax).'
                                type: string
                            type: object
                          withoutHeaders:
                            additionalProperties:
                              oneOf:
                              - not:
                                  anyOf:
                                  - required:
                                    - exact
                                  - required:
                                    - prefix
                                  - required:
                                    - regex
                              - required:
                                - exact
                              - required:
                                - prefix
                              - required:
                                - regex
                              properties:
                                exact:
                                  type: string
                                prefix:
                                  type: string
                                regex:
                                  description: '[RE2 style regex-based match](https://github.com/google/re2/wiki/Syntax).'
                                  type: string
                              type: object
                            description: withoutHeader has the same syntax with the
                              header, but has opposite meaning.
                            type: object
                        type: object
                      type: array
                    mirror:
                      description: Mirror HTTP traffic to a another destination in
                        addition to forwarding the requests to the intended destination.
                      properties:
                        host:
                          description: The name of a service from the service registry.
                          type: string
                        port:
                          description: Specifies the port on the host that is being
                            addressed.
                          properties:
                            number:
                              maximum: 4294967295
                              minimum: 0
                              type: integer
                          type: object
                        subset:
                          description: The name of a subset within the service.
                          type: string
                      required:
                      - host
                      type: object
                    mirror_percent:
                      maximum: 4294967295
                      minimum: 0
                      nullable: true
                      type: integer
                    mirrorPercent:
                      maximum: 4294967295
                      minimum: 0
                      nullable: true
                      type: integer
                    mirrorPercentage:
                      description: Percentage of the traffic to be mirrored by the
                        `mirror` field.
                      properties:
                        value:
                          format: double
                          type: number
                      type: object
                    mirrors:
                      description: Specifies the destinations to mirror HTTP traffic
                        in addition to the original destination.
                      items:
                        properties:
                          destination:
                            description: Destination specifies the target of the mirror
                              operation.
                            properties:
                              host:
                                description: The name of a service from the service
                                  registry.
                                type: string
                              port:
                                description: Specifies the port on the host that is
                                  being addressed.
                                properties:
                                  number:
                                    maximum: 4294967295
                                    minimum: 0
                                    type: integer
                                type: object
                              subset:
                                description: The name of a subset within the service.
                                type: string
                            required:
                            - host
                            type: object
                          percentage:
                            description: Percentage of the traffic to be mirrored
                              by the `destination` field.
                            properties:
                              value:
                                format: double
                                type: number
                            type: object
                        required:
                        - destination
                        type: object
                      type: array
                    name:
                      description: The name assigned to the route for debugging purposes.
                      type: string
                    redirect:
                      description: A HTTP rule can either return a direct_response,
                        redirect or forward (default) traffic.
                      oneOf:
                      - not:
                          anyOf:
                          - required:
                            - port
                          - required:
                            - derivePort
                      - required:
                        - port
                      - required:
                        - derivePort
                      properties:
                        authority:
                          description: On a redirect, overwrite the Authority/Host
                            portion of the URL with this value.
                          type: string
                        derivePort:
                          description: |-
                            On a redirect, dynamically set the port: * FROM_PROTOCOL_DEFAULT: automatically set to 80 for HTTP and 443 for HTTPS.

                            Valid Options: FROM_PROTOCOL_DEFAULT, FROM_REQUEST_PORT
                          enum:
                          - FROM_PROTOCOL_DEFAULT
                          - FROM_REQUEST_PORT
                          type: string
                        port:
                          description: On a redirect, overwrite the port portion of
                            the URL with this value.
                          maximum: 4294967295
                          minimum: 0
                          type: integer
                        redirectCode:
                          description: On a redirect, Specifies the HTTP status code
                            to use in the redirect response.
                          maximum: 4294967295
                          minimum: 0
                          type: integer
                        scheme:
                          description: On a redirect, overwrite the scheme portion
                            of the URL with this value.
                          type: string
                        uri:
                          description: On a redirect, overwrite the Path portion of
                            the URL with this value.
                          type: string
                      type: object
                    retries:
                      description: Retry policy for HTTP requests.
                      properties:
                        attempts:
                          description: Number of retries to be allowed for a given
                            request.
                          format: int32
                          type: integer
                        perTryTimeout:
                          description: Timeout per attempt for a given request, including
                            the initial call and any retries.
                          type: string
                          x-kubernetes-validations:
                          - message: must be a valid duration greater than 1ms
                            rule: duration(self) >= duration('1ms')
                        retryOn:
                          description: Specifies the conditions under which retry
                            takes place.
                          type: string
                        retryRemoteLocalities:
                          description: Flag to specify whether the retries should
                            retry to other localities.
                          nullable: true
                          type: boolean
                      type: object
                    rewrite:
                      description: Rewrite HTTP URIs and Authority headers.
                      properties:
                        authority:
                          description: rewrite the Authority/Host header with this
                            value.
                          type: string
                        uri:
                          description: rewrite the path (or the prefix) portion of
                            the URI with this value.
                          type: string
                        uriRegexRewrite:
                          description: rewrite the path portion of the URI with the
                            specified regex.
                          properties:
                            match:
                              description: '[RE2 style regex-based match](https://github.com/google/re2/wiki/Syntax).'
                              type: string
                            rewrite:
                              description: The string that should replace into matching
                                portions of original URI.
                              type: string
                          type: object
                      type: object
                    route:
                      description: A HTTP rule can either return a direct_response,
                        redirect or forward (default) traffic.
                      items:
                        properties:
                          destination:
                            description: Destination uniquely identifies the instances
                              of a service to which the request/connection should
                              be forwarded to.
                            properties:
                              host:
                                description: The name of a service from the service
                                  registry.
                                type: string
                              port:
                                description: Specifies the port on the host that is
                                  being addressed.
                                properties:
                                  number:
                                    maximum: 4294967295
                                    minimum: 0
                                    type: integer
                                type: object
                              subset:
                                description: The name of a subset within the service.
                                type: string
                            required:
                            - host
                            type: object
                          headers:
                            properties:
                              request:
                                properties:
                                  add:
                                    additionalProperties:
                                      type: string
                                    type: object
                                  remove:
                                    items:
                                      type: string
                                    type: array
                                  set:
                                    additionalProperties:
                                      type: string
                                    type: object
                                type: object
                              response:
                                properties:
                                  add:
                                    additionalProperties:
                                      type: string
                                    type: object
                                  remove:
                                    items:
                                      type: string
                                    type: array
                                  set:
                                    additionalProperties:
                                      type: string
                                    type: object
                                type: object
                            type: object
                          weight:
                            description: Weight specifies the relative proportion
                              of traffic to be forwarded to the destination.
                            format: int32
                            type: integer
                        required:
                        - destination
                        type: object
                      type: array
                    timeout:
                      description: Timeout for HTTP requests, default is disabled.
                      type: string
                      x-kubernetes-validations:
                      - message: must be a valid duration greater than 1ms
                        rule: duration(self) >= duration('1ms')
                  type: object
                type: array
              tcp:
                description: An ordered list of route rules for opaque TCP traffic.
                items:
                  properties:
                    match:
                      description: Match conditions to be satisfied for the rule to
                        be activated.
                      items:
                        properties:
                          destinationSubnets:
                            description: IPv4 or IPv6 ip addresses of destination
                              with optional subnet.
                            items:
                              type: string
                            type: array
                          gateways:
                            description: Names of gateways where the rule should be
                              applied.
                            items:
                              type: string
                            type: array
                          port:
                            description: Specifies the port on the host that is being
                              addressed.
                            maximum: 4294967295
                            minimum: 0
                            type: integer
                          sourceLabels:
                            additionalProperties:
                              type: string
                            description: One or more labels that constrain the applicability
                              of a rule to workloads with the given labels.
                            type: object
                          sourceNamespace:
                            description: Source namespace constraining the applicability
                              of a rule to workloads in that namespace.
                            type: string
                          sourceSubnet:
                            type: string
                        type: object
                      type: array
                    route:
                      description: The destination to which the connection should
                        be forwarded to.
                      items:
                        properties:
                          destination:
                            description: Destination uniquely identifies the instances
                              of a service to which the request/connection should
                              be forwarded to.
                            properties:
                              host:
                                description: The name of a service from the service
                                  registry.
                                type: string
                              port:
                                description: Specifies the port on the host that is
                                  being addressed.
                                properties:
                                  number:
                                    maximum: 4294967295
                                    minimum: 0
                                    type: integer
                                type: object
                              subset:
                                description: The name of a subset within the service.
                                type: string
                            required:
                            - host
                            type: object
                          weight:
                            description: Weight specifies the relative proportion
                              of traffic to be forwarded to the destination.
                            format: int32
                            type: integer
                        required:
                        - destination
                        type: object
                      type: array
                  type: object
                type: array
              tls:
                description: An ordered list of route rule for non-terminated TLS
                  & HTTPS traffic.
                items:
                  properties:
                    match:
                      description: Match conditions to be satisfied for the rule to
                        be activated.
                      items:
                        properties:
                          destinationSubnets:
                            description: IPv4 or IPv6 ip addresses of destination
                              with optional subnet.
                            items:
                              type: string
                            type: array
                          gateways:
                            description: Names of gateways where the rule should be
                              applied.
                            items:
                              type: string
                            type: array
                          port:
                            description: Specifies the port on the host that is being
                              addressed.
                            maximum: 4294967295
                            minimum: 0
                            type: integer
                          sniHosts:
                            description: SNI (server name indicator) to match on.
                            items:
                              type: string
                            type: array
                          sourceLabels:
                            additionalProperties:
                              type: string
                            description: One or more labels that constrain the applicability
                              of a rule to workloads with the given labels.
                            type: object
                          sourceNamespace:
                            description: Source namespace constraining the applicability
                              of a rule to workloads in that namespace.
                            type: string
                        required:
                        - sniHosts
                        type: object
                      type: array
                    route:
                      description: The destination to which the connection should
                        be forwarded to.
                      items:
                        properties:
                          destination:
                            description: Destination uniquely identifies the instances
                              of a service to which the request/connection should
                              be forwarded to.
                            properties:
                              host:
                                description: The name of a service from the service
                                  registry.
                                type: string
                              port:
                                description: Specifies the port on the host that is
                                  being addressed.
                                properties:
                                  number:
                                    maximum: 4294967295
                                    minimum: 0
                                    type: integer
                                type: object
                              subset:
                                description: The name of a subset within the service.
                                type: string
                            required:
                            - host
                            type: object
                          weight:
                            description: Weight specifies the relative proportion
                              of traffic to be forwarded to the destination.
                            format: int32
                            type: integer
                        required:
                        - destination
                        type: object
                      type: array
                  required:
                  - match
                  type: object
                type: array
            type: object
          status:
            type: object
            x-kubernetes-preserve-unknown-fields: true
        type: object
    served: true
    storage: false
    subresources:
      status: {}
  - additionalPrinterColumns:
    - description: The names of gateways and sidecars that should apply these routes
      jsonPath: .spec.gateways
      name: Gateways
      type: string
    - description: The destination hosts to which traffic is being sent
      jsonPath: .spec.hosts
      name: Hosts
      type: string
    - description: 'CreationTimestamp is a timestamp representing the server time
        when this object was created. It is not guaranteed to be set in happens-before
        order across separate operations. Clients may not set this value. It is represented
        in RFC3339 form and is in UTC. Populated by the system. Read-only. Null for
        lists. More info: https://git.k8s.io/community/contributors/devel/api-conventions.md#metadata'
      jsonPath: .metadata.creationTimestamp
      name: Age
      type: date
    name: v1beta1
    schema:
      openAPIV3Schema:
        properties:
          spec:
            description: 'Configuration affecting label/content routing, sni routing,
              etc. See more details at: https://istio.io/docs/reference/config/networking/virtual-service.html'
            properties:
              exportTo:
                description: A list of namespaces to which this virtual service is
                  exported.
                items:
                  type: string
                type: array
              gateways:
                description: The names of gateways and sidecars that should apply
                  these routes.
                items:
                  type: string
                type: array
              hosts:
                description: The destination hosts to which traffic is being sent.
                items:
                  type: string
                type: array
              http:
                description: An ordered list of route rules for HTTP traffic.
                items:
                  properties:
                    corsPolicy:
                      description: Cross-Origin Resource Sharing policy (CORS).
                      properties:
                        allowCredentials:
                          description: Indicates whether the caller is allowed to
                            send the actual request (not the preflight) using credentials.
                          nullable: true
                          type: boolean
                        allowHeaders:
                          description: List of HTTP headers that can be used when
                            requesting the resource.
                          items:
                            type: string
                          type: array
                        allowMethods:
                          description: List of HTTP methods allowed to access the
                            resource.
                          items:
                            type: string
                          type: array
                        allowOrigin:
                          items:
                            type: string
                          type: array
                        allowOrigins:
                          description: String patterns that match allowed origins.
                          items:
                            oneOf:
                            - not:
                                anyOf:
                                - required:
                                  - exact
                                - required:
                                  - prefix
                                - required:
                                  - regex
                            - required:
                              - exact
                            - required:
                              - prefix
                            - required:
                              - regex
                            properties:
                              exact:
                                type: string
                              prefix:
                                type: string
                              regex:
                                description: '[RE2 style regex-based match](https://github.com/google/re2/wiki/Syntax).'
                                type: string
                            type: object
                          type: array
                        exposeHeaders:
                          description: A list of HTTP headers that the browsers are
                            allowed to access.
                          items:
                            type: string
                          type: array
                        maxAge:
                          description: Specifies how long the results of a preflight
                            request can be cached.
                          type: string
                          x-kubernetes-validations:
                          - message: must be a valid duration greater than 1ms
                            rule: duration(self) >= duration('1ms')
                        unmatchedPreflights:
                          description: |-
                            Indicates whether preflight requests not matching the configured allowed origin shouldn't be forwarded to the upstream.

                            Valid Options: FORWARD, IGNORE
                          enum:
                          - UNSPECIFIED
                          - FORWARD
                          - IGNORE
                          type: string
                      type: object
                    delegate:
                      description: Delegate is used to specify the particular VirtualService
                        which can be used to define delegate HTTPRoute.
                      properties:
                        name:
                          description: Name specifies the name of the delegate VirtualService.
                          type: string
                        namespace:
                          description: Namespace specifies the namespace where the
                            delegate VirtualService resides.
                          type: string
                      type: object
                    directResponse:
                      description: A HTTP rule can either return a direct_response,
                        redirect or forward (default) traffic.
                      properties:
                        body:
                          description: Specifies the content of the response body.
                          oneOf:
                          - not:
                              anyOf:
                              - required:
                                - string
                              - required:
                                - bytes
                          - required:
                            - string
                          - required:
                            - bytes
                          properties:
                            bytes:
                              description: response body as base64 encoded bytes.
                              format: binary
                              type: string
                            string:
                              type: string
                          type: object
                        status:
                          description: Specifies the HTTP response status to be returned.
                          maximum: 4294967295
                          minimum: 0
                          type: integer
                      required:
                      - status
                      type: object
                    fault:
                      description: Fault injection policy to apply on HTTP traffic
                        at the client side.
                      properties:
                        abort:
                          description: Abort Http request attempts and return error
                            codes back to downstream service, giving the impression
                            that the upstream service is faulty.
                          oneOf:
                          - not:
                              anyOf:
                              - required:
                                - httpStatus
                              - required:
                                - grpcStatus
                              - required:
                                - http2Error
                          - required:
                            - httpStatus
                          - required:
                            - grpcStatus
                          - required:
                            - http2Error
                          properties:
                            grpcStatus:
                              description: GRPC status code to use to abort the request.
                              type: string
                            http2Error:
                              type: string
                            httpStatus:
                              description: HTTP status code to use to abort the Http
                                request.
                              format: int32
                              type: integer
                            percentage:
                              description: Percentage of requests to be aborted with
                                the error code provided.
                              properties:
                                value:
                                  format: double
                                  type: number
                              type: object
                          type: object
                        delay:
                          description: Delay requests before forwarding, emulating
                            various failures such as network issues, overloaded upstream
                            service, etc.
                          oneOf:
                          - not:
                              anyOf:
                              - required:
                                - fixedDelay
                              - required:
                                - exponentialDelay
                          - required:
                            - fixedDelay
                          - required:
                            - exponentialDelay
                          properties:
                            exponentialDelay:
                              type: string
                              x-kubernetes-validations:
                              - message: must be a valid duration greater than 1ms
                                rule: duration(self) >= duration('1ms')
                            fixedDelay:
                              description: Add a fixed delay before forwarding the
                                request.
                              type: string
                              x-kubernetes-validations:
                              - message: must be a valid duration greater than 1ms
                                rule: duration(self) >= duration('1ms')
                            percent:
                              description: Percentage of requests on which the delay
                                will be injected (0-100).
                              format: int32
                              type: integer
                            percentage:
                              description: Percentage of requests on which the delay
                                will be injected.
                              properties:
                                value:
                                  format: double
                                  type: number
                              type: object
                          type: object
                      type: object
                    headers:
                      properties:
                        request:
                          properties:
                            add:
                              additionalProperties:
                                type: string
                              type: object
                            remove:
                              items:
                                type: string
                              type: array
                            set:
                              additionalProperties:
                                type: string
                              type: object
                          type: object
                        response:
                          properties:
                            add:
                              additionalProperties:
                                type: string
                              type: object
                            remove:
                              items:
                                type: string
                              type: array
                            set:
                              additionalProperties:
                                type: string
                              type: object
                          type: object
                      type: object
                    match:
                      description: Match conditions to be satisfied for the rule to
                        be activated.
                      items:
                        properties:
                          authority:
                            description: 'HTTP Authority values are case-sensitive
                              and formatted as follows: - `exact: "value"` for exact
                              string match - `prefix: "value"` for prefix-based match
                              - `regex: "value"` for [RE2 style regex-based match](https://github.com/google/re2/wiki/Syntax).'
                            oneOf:
                            - not:
                                anyOf:
                                - required:
                                  - exact
                                - required:
                                  - prefix
                                - required:
                                  - regex
                            - required:
                              - exact
                            - required:
                              - prefix
                            - required:
                              - regex
                            properties:
                              exact:
                                type: string
                              prefix:
                                type: string
                              regex:
                                description: '[RE2 style regex-based match](https://github.com/google/re2/wiki/Syntax).'
                                type: string
                            type: object
                          gateways:
                            description: Names of gateways where the rule should be
                              applied.
                            items:
                              type: string
                            type: array
                          headers:
                            additionalProperties:
                              oneOf:
                              - not:
                                  anyOf:
                                  - required:
                                    - exact
                                  - required:
                                    - prefix
                                  - required:
                                    - regex
                              - required:
                                - exact
                              - required:
                                - prefix
                              - required:
                                - regex
                              properties:
                                exact:
                                  type: string
                                prefix:
                                  type: string
                                regex:
                                  description: '[RE2 style regex-based match](https://github.com/google/re2/wiki/Syntax).'
                                  type: string
                              type: object
                            description: The header keys must be lowercase and use
                              hyphen as the separator, e.g.
                            type: object
                          ignoreUriCase:
                            description: Flag to specify whether the URI matching
                              should be case-insensitive.
                            type: boolean
                          method:
                            description: 'HTTP Method values are case-sensitive and
                              formatted as follows: - `exact: "value"` for exact string
                              match - `prefix: "value"` for prefix-based match - `regex:
                              "value"` for [RE2 style regex-based match](https://github.com/google/re2/wiki/Syntax).'
                            oneOf:
                            - not:
                                anyOf:
                                - required:
                                  - exact
                                - required:
                                  - prefix
                                - required:
                                  - regex
                            - required:
                              - exact
                            - required:
                              - prefix
                            - required:
                              - regex
                            properties:
                              exact:
                                type: string
                              prefix:
                                type: string
                              regex:
                                description: '[RE2 style regex-based match](https://github.com/google/re2/wiki/Syntax).'
                                type: string
                            type: object
                          name:
                            description: The name assigned to a match.
                            type: string
                          port:
                            description: Specifies the ports on the host that is being
                              addressed.
                            maximum: 4294967295
                            minimum: 0
                            type: integer
                          queryParams:
                            additionalProperties:
                              oneOf:
                              - not:
                                  anyOf:
                                  - required:
                                    - exact
                                  - required:
                                    - prefix
                                  - required:
                                    - regex
                              - required:
                                - exact
                              - required:
                                - prefix
                              - required:
                                - regex
                              properties:
                                exact:
                                  type: string
                                prefix:
                                  type: string
                                regex:
                                  description: '[RE2 style regex-based match](https://github.com/google/re2/wiki/Syntax).'
                                  type: string
                              type: object
                            description: Query parameters for matching.
                            type: object
                          scheme:
                            description: 'URI Scheme values are case-sensitive and
                              formatted as follows: - `exact: "value"` for exact string
                              match - `prefix: "value"` for prefix-based match - `regex:
                              "value"` for [RE2 style regex-based match](https://github.com/google/re2/wiki/Syntax).'
                            oneOf:
                            - not:
                                anyOf:
                                - required:
                                  - exact
                                - required:
                                  - prefix
                                - required:
                                  - regex
                            - required:
                              - exact
                            - required:
                              - prefix
                            - required:
                              - regex
                            properties:
                              exact:
                                type: string
                              prefix:
                                type: string
                              regex:
                                description: '[RE2 style regex-based match](https://github.com/google/re2/wiki/Syntax).'
                                type: string
                            type: object
                          sourceLabels:
                            additionalProperties:
                              type: string
                            description: One or more labels that constrain the applicability
                              of a rule to source (client) workloads with the given
                              labels.
                            type: object
                          sourceNamespace:
                            description: Source namespace constraining the applicability
                              of a rule to workloads in that namespace.
                            type: string
                          statPrefix:
                            description: The human readable prefix to use when emitting
                              statistics for this route.
                            type: string
                          uri:
                            description: 'URI to match values are case-sensitive and
                              formatted as follows: - `exact: "value"` for exact string
                              match - `prefix: "value"` for prefix-based match - `regex:
                              "value"` for [RE2 style regex-based match](https://github.com/google/re2/wiki/Syntax).'
                            oneOf:
                            - not:
                                anyOf:
                                - required:
                                  - exact
                                - required:
                                  - prefix
                                - required:
                                  - regex
                            - required:
                              - exact
                            - required:
                              - prefix
                            - required:
                              - regex
                            properties:
                              exact:
                                type: string
                              prefix:
                                type: string
                              regex:
                                description: '[RE2 style regex-based match](https://github.com/google/re2/wiki/Syntax).'
                                type: string
                            type: object
                          withoutHeaders:
                            additionalProperties:
                              oneOf:
                              - not:
                                  anyOf:
                                  - required:
                                    - exact
                                  - required:
                                    - prefix
                                  - required:
                                    - regex
                              - required:
                                - exact
                              - required:
                                - prefix
                              - required:
                                - regex
                              properties:
                                exact:
                                  type: string
                                prefix:
                                  type: string
                                regex:
                                  description: '[RE2 style regex-based match](https://github.com/google/re2/wiki/Syntax).'
                                  type: string
                              type: object
                            description: withoutHeader has the same syntax with the
                              header, but has opposite meaning.
                            type: object
                        type: object
                      type: array
                    mirror:
                      description: Mirror HTTP traffic to a another destination in
                        addition to forwarding the requests to the intended destination.
                      properties:
                        host:
                          description: The name of a service from the service registry.
                          type: string
                        port:
                          description: Specifies the port on the host that is being
                            addressed.
                          properties:
                            number:
                              maximum: 4294967295
                              minimum: 0
                              type: integer
                          type: object
                        subset:
                          description: The name of a subset within the service.
                          type: string
                      required:
                      - host
                      type: object
                    mirror_percent:
                      maximum: 4294967295
                      minimum: 0
                      nullable: true
                      type: integer
                    mirrorPercent:
                      maximum: 4294967295
                      minimum: 0
                      nullable: true
                      type: integer
                    mirrorPercentage:
                      description: Percentage of the traffic to be mirrored by the
                        `mirror` field.
                      properties:
                        value:
                          format: double
                          type: number
                      type: object
                    mirrors:
                      description: Specifies the destinations to mirror HTTP traffic
                        in addition to the original destination.
                      items:
                        properties:
                          destination:
                            description: Destination specifies the target of the mirror
                              operation.
                            properties:
                              host:
                                description: The name of a service from the service
                                  registry.
                                type: string
                              port:
                                description: Specifies the port on the host that is
                                  being addressed.
                                properties:
                                  number:
                                    maximum: 4294967295
                                    minimum: 0
                                    type: integer
                                type: object
                              subset:
                                description: The name of a subset within the service.
                                type: string
                            required:
                            - host
                            type: object
                          percentage:
                            description: Percentage of the traffic to be mirrored
                              by the `destination` field.
                            properties:
                              value:
                                format: double
                                type: number
                            type: object
                        required:
                        - destination
                        type: object
                      type: array
                    name:
                      description: The name assigned to the route for debugging purposes.
                      type: string
                    redirect:
                      description: A HTTP rule can either return a direct_response,
                        redirect or forward (default) traffic.
                      oneOf:
                      - not:
                          anyOf:
                          - required:
                            - port
                          - required:
                            - derivePort
                      - required:
                        - port
                      - required:
                        - derivePort
                      properties:
                        authority:
                          description: On a redirect, overwrite the Authority/Host
                            portion of the URL with this value.
                          type: string
                        derivePort:
                          description: |-
                            On a redirect, dynamically set the port: * FROM_PROTOCOL_DEFAULT: automatically set to 80 for HTTP and 443 for HTTPS.

                            Valid Options: FROM_PROTOCOL_DEFAULT, FROM_REQUEST_PORT
                          enum:
                          - FROM_PROTOCOL_DEFAULT
                          - FROM_REQUEST_PORT
                          type: string
                        port:
                          description: On a redirect, overwrite the port portion of
                            the URL with this value.
                          maximum: 4294967295
                          minimum: 0
                          type: integer
                        redirectCode:
                          description: On a redirect, Specifies the HTTP status code
                            to use in the redirect response.
                          maximum: 4294967295
                          minimum: 0
                          type: integer
                        scheme:
                          description: On a redirect, overwrite the scheme portion
                            of the URL with this value.
                          type: string
                        uri:
                          description: On a redirect, overwrite the Path portion of
                            the URL with this value.
                          type: string
                      type: object
                    retries:
                      description: Retry policy for HTTP requests.
                      properties:
                        attempts:
                          description: Number of retries to be allowed for a given
                            request.
                          format: int32
                          type: integer
                        perTryTimeout:
                          description: Timeout per attempt for a given request, including
                            the initial call and any retries.
                          type: string
                          x-kubernetes-validations:
                          - message: must be a valid duration greater than 1ms
                            rule: duration(self) >= duration('1ms')
                        retryOn:
                          description: Specifies the conditions under which retry
                            takes place.
                          type: string
                        retryRemoteLocalities:
                          description: Flag to specify whether the retries should
                            retry to other localities.
                          nullable: true
                          type: boolean
                      type: object
                    rewrite:
                      description: Rewrite HTTP URIs and Authority headers.
                      properties:
                        authority:
                          description: rewrite the Authority/Host header with this
                            value.
                          type: string
                        uri:
                          description: rewrite the path (or the prefix) portion of
                            the URI with this value.
                          type: string
                        uriRegexRewrite:
                          description: rewrite the path portion of the URI with the
                            specified regex.
                          properties:
                            match:
                              description: '[RE2 style regex-based match](https://github.com/google/re2/wiki/Syntax).'
                              type: string
                            rewrite:
                              description: The string that should replace into matching
                                portions of original URI.
                              type: string
                          type: object
                      type: object
                    route:
                      description: A HTTP rule can either return a direct_response,
                        redirect or forward (default) traffic.
                      items:
                        properties:
                          destination:
                            description: Destination uniquely identifies the instances
                              of a service to which the request/connection should
                              be forwarded to.
                            properties:
                              host:
                                description: The name of a service from the service
                                  registry.
                                type: string
                              port:
                                description: Specifies the port on the host that is
                                  being addressed.
                                properties:
                                  number:
                                    maximum: 4294967295
                                    minimum: 0
                                    type: integer
                                type: object
                              subset:
                                description: The name of a subset within the service.
                                type: string
                            required:
                            - host
                            type: object
                          headers:
                            properties:
                              request:
                                properties:
                                  add:
                                    additionalProperties:
                                      type: string
                                    type: object
                                  remove:
                                    items:
                                      type: string
                                    type: array
                                  set:
                                    additionalProperties:
                                      type: string
                                    type: object
                                type: object
                              response:
                                properties:
                                  add:
                                    additionalProperties:
                                      type: string
                                    type: object
                                  remove:
                                    items:
                                      type: string
                                    type: array
                                  set:
                                    additionalProperties:
                                      type: string
                                    type: object
                                type: object
                            type: object
                          weight:
                            description: Weight specifies the relative proportion
                              of traffic to be forwarded to the destination.
                            format: int32
                            type: integer
                        required:
                        - destination
                        type: object
                      type: array
                    timeout:
                      description: Timeout for HTTP requests, default is disabled.
                      type: string
                      x-kubernetes-validations:
                      - message: must be a valid duration greater than 1ms
                        rule: duration(self) >= duration('1ms')
                  type: object
                type: array
              tcp:
                description: An ordered list of route rules for opaque TCP traffic.
                items:
                  properties:
                    match:
                      description: Match conditions to be satisfied for the rule to
                        be activated.
                      items:
                        properties:
                          destinationSubnets:
                            description: IPv4 or IPv6 ip addresses of destination
                              with optional subnet.
                            items:
                              type: string
                            type: array
                          gateways:
                            description: Names of gateways where the rule should be
                              applied.
                            items:
                              type: string
                            type: array
                          port:
                            description: Specifies the port on the host that is being
                              addressed.
                            maximum: 4294967295
                            minimum: 0
                            type: integer
                          sourceLabels:
                            additionalProperties:
                              type: string
                            description: One or more labels that constrain the applicability
                              of a rule to workloads with the given labels.
                            type: object
                          sourceNamespace:
                            description: Source namespace constraining the applicability
                              of a rule to workloads in that namespace.
                            type: string
                          sourceSubnet:
                            type: string
                        type: object
                      type: array
                    route:
                      description: The destination to which the connection should
                        be forwarded to.
                      items:
                        properties:
                          destination:
                            description: Destination uniquely identifies the instances
                              of a service to which the request/connection should
                              be forwarded to.
                            properties:
                              host:
                                description: The name of a service from the service
                                  registry.
                                type: string
                              port:
                                description: Specifies the port on the host that is
                                  being addressed.
                                properties:
                                  number:
                                    maximum: 4294967295
                                    minimum: 0
                                    type: integer
                                type: object
                              subset:
                                description: The name of a subset within the service.
                                type: string
                            required:
                            - host
                            type: object
                          weight:
                            description: Weight specifies the relative proportion
                              of traffic to be forwarded to the destination.
                            format: int32
                            type: integer
                        required:
                        - destination
                        type: object
                      type: array
                  type: object
                type: array
              tls:
                description: An ordered list of route rule for non-terminated TLS
                  & HTTPS traffic.
                items:
                  properties:
                    match:
                      description: Match conditions to be satisfied for the rule to
                        be activated.
                      items:
                        properties:
                          destinationSubnets:
                            description: IPv4 or IPv6 ip addresses of destination
                              with optional subnet.
                            items:
                              type: string
                            type: array
                          gateways:
                            description: Names of gateways where the rule should be
                              applied.
                            items:
                              type: string
                            type: array
                          port:
                            description: Specifies the port on the host that is being
                              addressed.
                            maximum: 4294967295
                            minimum: 0
                            type: integer
                          sniHosts:
                            description: SNI (server name indicator) to match on.
                            items:
                              type: string
                            type: array
                          sourceLabels:
                            additionalProperties:
                              type: string
                            description: One or more labels that constrain the applicability
                              of a rule to workloads with the given labels.
                            type: object
                          sourceNamespace:
                            description: Source namespace constraining the applicability
                              of a rule to workloads in that namespace.
                            type: string
                        required:
                        - sniHosts
                        type: object
                      type: array
                    route:
                      description: The destination to which the connection should
                        be forwarded to.
                      items:
                        properties:
                          destination:
                            description: Destination uniquely identifies the instances
                              of a service to which the request/connection should
                              be forwarded to.
                            properties:
                              host:
                                description: The name of a service from the service
                                  registry.
                                type: string
                              port:
                                description: Specifies the port on the host that is
                                  being addressed.
                                properties:
                                  number:
                                    maximum: 4294967295
                                    minimum: 0
                                    type: integer
                                type: object
                              subset:
                                description: The name of a subset within the service.
                                type: string
                            required:
                            - host
                            type: object
                          weight:
                            description: Weight specifies the relative proportion
                              of traffic to be forwarded to the destination.
                            format: int32
                            type: integer
                        required:
                        - destination
                        type: object
                      type: array
                  required:
                  - match
                  type: object
                type: array
            type: object
          status:
            type: object
            x-kubernetes-preserve-unknown-fields: true
        type: object
    served: true
    storage: true
    subresources:
      status: {}
---
# Source: istio-microk8s/charts/base/templates/crds.yaml
apiVersion: apiextensions.k8s.io/v1
kind: CustomResourceDefinition
metadata:
  annotations:
    "helm.sh/resource-policy": keep
  labels:
    app: istio-pilot
    chart: istio
    heritage: Tiller
    release: istio
  name: workloadentries.networking.istio.io
spec:
  group: networking.istio.io
  names:
    categories:
    - istio-io
    - networking-istio-io
    kind: WorkloadEntry
    listKind: WorkloadEntryList
    plural: workloadentries
    shortNames:
    - we
    singular: workloadentry
  scope: Namespaced
  versions:
  - additionalPrinterColumns:
    - description: 'CreationTimestamp is a timestamp representing the server time
        when this object was created. It is not guaranteed to be set in happens-before
        order across separate operations. Clients may not set this value. It is represented
        in RFC3339 form and is in UTC. Populated by the system. Read-only. Null for
        lists. More info: https://git.k8s.io/community/contributors/devel/api-conventions.md#metadata'
      jsonPath: .metadata.creationTimestamp
      name: Age
      type: date
    - description: Address associated with the network endpoint.
      jsonPath: .spec.address
      name: Address
      type: string
    name: v1
    schema:
      openAPIV3Schema:
        properties:
          spec:
            description: 'Configuration affecting VMs onboarded into the mesh. See
              more details at: https://istio.io/docs/reference/config/networking/workload-entry.html'
            properties:
              address:
                description: Address associated with the network endpoint without
                  the port.
                maxLength: 256
                type: string
                x-kubernetes-validations:
                - message: UDS must be an absolute path or abstract socket
                  rule: 'self.startsWith(''unix://'') ? (self.substring(7,8) == ''/''
                    || self.substring(7,8) == ''@'') : true'
                - message: UDS may not be a dir
                  rule: 'self.startsWith(''unix://'') ? !self.endsWith(''/'') : true'
              labels:
                additionalProperties:
                  type: string
                description: One or more labels associated with the endpoint.
                maxProperties: 256
                type: object
              locality:
                description: The locality associated with the endpoint.
                maxLength: 2048
                type: string
              network:
                description: Network enables Istio to group endpoints resident in
                  the same L3 domain/network.
                maxLength: 2048
                type: string
              ports:
                additionalProperties:
                  maximum: 4294967295
                  minimum: 0
                  type: integer
                  x-kubernetes-validations:
                  - message: port must be between 1-65535
                    rule: 0 < self && self <= 65535
                description: Set of ports associated with the endpoint.
                maxProperties: 128
                type: object
                x-kubernetes-validations:
                - message: port name must be valid
                  rule: self.all(key, size(key) < 63 && key.matches('^[a-zA-Z0-9](?:[-a-zA-Z0-9]*[a-zA-Z0-9])?$'))
              serviceAccount:
                description: The service account associated with the workload if a
                  sidecar is present in the workload.
                maxLength: 253
                type: string
              weight:
                description: The load balancing weight associated with the endpoint.
                maximum: 4294967295
                minimum: 0
                type: integer
            type: object
            x-kubernetes-validations:
            - message: Address is required
              rule: has(self.address) || has(self.network)
            - message: UDS may not include ports
              rule: '(has(self.address) && self.address.startsWith(''unix://'')) ?
                !has(self.ports) : true'
          status:
            type: object
            x-kubernetes-preserve-unknown-fields: true
        required:
        - spec
        - spec
        - spec
        type: object
    served: true
    storage: false
    subresources:
      status: {}
  - additionalPrinterColumns:
    - description: 'CreationTimestamp is a timestamp representing the server time
        when this object was created. It is not guaranteed to be set in happens-before
        order across separate operations. Clients may not set this value. It is represented
        in RFC3339 form and is in UTC. Populated by the system. Read-only. Null for
        lists. More info: https://git.k8s.io/community/contributors/devel/api-conventions.md#metadata'
      jsonPath: .metadata.creationTimestamp
      name: Age
      type: date
    - description: Address associated with the network endpoint.
      jsonPath: .spec.address
      name: Address
      type: string
    name: v1alpha3
    schema:
      openAPIV3Schema:
        properties:
          spec:
            description: 'Configuration affecting VMs onboarded into the mesh. See
              more details at: https://istio.io/docs/reference/config/networking/workload-entry.html'
            properties:
              address:
                description: Address associated with the network endpoint without
                  the port.
                maxLength: 256
                type: string
                x-kubernetes-validations:
                - message: UDS must be an absolute path or abstract socket
                  rule: 'self.startsWith(''unix://'') ? (self.substring(7,8) == ''/''
                    || self.substring(7,8) == ''@'') : true'
                - message: UDS may not be a dir
                  rule: 'self.startsWith(''unix://'') ? !self.endsWith(''/'') : true'
              labels:
                additionalProperties:
                  type: string
                description: One or more labels associated with the endpoint.
                maxProperties: 256
                type: object
              locality:
                description: The locality associated with the endpoint.
                maxLength: 2048
                type: string
              network:
                description: Network enables Istio to group endpoints resident in
                  the same L3 domain/network.
                maxLength: 2048
                type: string
              ports:
                additionalProperties:
                  maximum: 4294967295
                  minimum: 0
                  type: integer
                  x-kubernetes-validations:
                  - message: port must be between 1-65535
                    rule: 0 < self && self <= 65535
                description: Set of ports associated with the endpoint.
                maxProperties: 128
                type: object
                x-kubernetes-validations:
                - message: port name must be valid
                  rule: self.all(key, size(key) < 63 && key.matches('^[a-zA-Z0-9](?:[-a-zA-Z0-9]*[a-zA-Z0-9])?$'))
              serviceAccount:
                description: The service account associated with the workload if a
                  sidecar is present in the workload.
                maxLength: 253
                type: string
              weight:
                description: The load balancing weight associated with the endpoint.
                maximum: 4294967295
                minimum: 0
                type: integer
            type: object
            x-kubernetes-validations:
            - message: Address is required
              rule: has(self.address) || has(self.network)
            - message: UDS may not include ports
              rule: '(has(self.address) && self.address.startsWith(''unix://'')) ?
                !has(self.ports) : true'
          status:
            type: object
            x-kubernetes-preserve-unknown-fields: true
        required:
        - spec
        - spec
        - spec
        type: object
    served: true
    storage: false
    subresources:
      status: {}
  - additionalPrinterColumns:
    - description: 'CreationTimestamp is a timestamp representing the server time
        when this object was created. It is not guaranteed to be set in happens-before
        order across separate operations. Clients may not set this value. It is represented
        in RFC3339 form and is in UTC. Populated by the system. Read-only. Null for
        lists. More info: https://git.k8s.io/community/contributors/devel/api-conventions.md#metadata'
      jsonPath: .metadata.creationTimestamp
      name: Age
      type: date
    - description: Address associated with the network endpoint.
      jsonPath: .spec.address
      name: Address
      type: string
    name: v1beta1
    schema:
      openAPIV3Schema:
        properties:
          spec:
            description: 'Configuration affecting VMs onboarded into the mesh. See
              more details at: https://istio.io/docs/reference/config/networking/workload-entry.html'
            properties:
              address:
                description: Address associated with the network endpoint without
                  the port.
                maxLength: 256
                type: string
                x-kubernetes-validations:
                - message: UDS must be an absolute path or abstract socket
                  rule: 'self.startsWith(''unix://'') ? (self.substring(7,8) == ''/''
                    || self.substring(7,8) == ''@'') : true'
                - message: UDS may not be a dir
                  rule: 'self.startsWith(''unix://'') ? !self.endsWith(''/'') : true'
              labels:
                additionalProperties:
                  type: string
                description: One or more labels associated with the endpoint.
                maxProperties: 256
                type: object
              locality:
                description: The locality associated with the endpoint.
                maxLength: 2048
                type: string
              network:
                description: Network enables Istio to group endpoints resident in
                  the same L3 domain/network.
                maxLength: 2048
                type: string
              ports:
                additionalProperties:
                  maximum: 4294967295
                  minimum: 0
                  type: integer
                  x-kubernetes-validations:
                  - message: port must be between 1-65535
                    rule: 0 < self && self <= 65535
                description: Set of ports associated with the endpoint.
                maxProperties: 128
                type: object
                x-kubernetes-validations:
                - message: port name must be valid
                  rule: self.all(key, size(key) < 63 && key.matches('^[a-zA-Z0-9](?:[-a-zA-Z0-9]*[a-zA-Z0-9])?$'))
              serviceAccount:
                description: The service account associated with the workload if a
                  sidecar is present in the workload.
                maxLength: 253
                type: string
              weight:
                description: The load balancing weight associated with the endpoint.
                maximum: 4294967295
                minimum: 0
                type: integer
            type: object
            x-kubernetes-validations:
            - message: Address is required
              rule: has(self.address) || has(self.network)
            - message: UDS may not include ports
              rule: '(has(self.address) && self.address.startsWith(''unix://'')) ?
                !has(self.ports) : true'
          status:
            type: object
            x-kubernetes-preserve-unknown-fields: true
        required:
        - spec
        - spec
        - spec
        type: object
    served: true
    storage: true
    subresources:
      status: {}
---
# Source: istio-microk8s/charts/base/templates/crds.yaml
apiVersion: apiextensions.k8s.io/v1
kind: CustomResourceDefinition
metadata:
  labels:
    app: istio-pilot
    chart: istio
    heritage: Tiller
    release: istio
  name: workloadgroups.networking.istio.io
spec:
  group: networking.istio.io
  names:
    categories:
    - istio-io
    - networking-istio-io
    kind: WorkloadGroup
    listKind: WorkloadGroupList
    plural: workloadgroups
    shortNames:
    - wg
    singular: workloadgroup
  scope: Namespaced
  versions:
  - additionalPrinterColumns:
    - description: 'CreationTimestamp is a timestamp representing the server time
        when this object was created. It is not guaranteed to be set in happens-before
        order across separate operations. Clients may not set this value. It is represented
        in RFC3339 form and is in UTC. Populated by the system. Read-only. Null for
        lists. More info: https://git.k8s.io/community/contributors/devel/api-conventions.md#metadata'
      jsonPath: .metadata.creationTimestamp
      name: Age
      type: date
    name: v1
    schema:
      openAPIV3Schema:
        properties:
          spec:
            description: 'Describes a collection of workload instances. See more details
              at: https://istio.io/docs/reference/config/networking/workload-group.html'
            properties:
              metadata:
                description: Metadata that will be used for all corresponding `WorkloadEntries`.
                properties:
                  annotations:
                    additionalProperties:
                      type: string
                    type: object
                  labels:
                    additionalProperties:
                      type: string
                    type: object
                type: object
              probe:
                description: '`ReadinessProbe` describes the configuration the user
                  must provide for healthchecking on their workload.'
                oneOf:
                - not:
                    anyOf:
                    - required:
                      - httpGet
                    - required:
                      - tcpSocket
                    - required:
                      - exec
                - required:
                  - httpGet
                - required:
                  - tcpSocket
                - required:
                  - exec
                properties:
                  exec:
                    description: Health is determined by how the command that is executed
                      exited.
                    properties:
                      command:
                        description: Command to run.
                        items:
                          type: string
                        type: array
                    type: object
                  failureThreshold:
                    description: Minimum consecutive failures for the probe to be
                      considered failed after having succeeded.
                    format: int32
                    type: integer
                  httpGet:
                    description: '`httpGet` is performed to a given endpoint and the
                      status/able to connect determines health.'
                    properties:
                      host:
                        description: Host name to connect to, defaults to the pod
                          IP.
                        type: string
                      httpHeaders:
                        description: Headers the proxy will pass on to make the request.
                        items:
                          properties:
                            name:
                              type: string
                            value:
                              type: string
                          type: object
                        type: array
                      path:
                        description: Path to access on the HTTP server.
                        type: string
                      port:
                        description: Port on which the endpoint lives.
                        maximum: 4294967295
                        minimum: 0
                        type: integer
                      scheme:
                        type: string
                    required:
                    - port
                    type: object
                  initialDelaySeconds:
                    description: Number of seconds after the container has started
                      before readiness probes are initiated.
                    format: int32
                    type: integer
                  periodSeconds:
                    description: How often (in seconds) to perform the probe.
                    format: int32
                    type: integer
                  successThreshold:
                    description: Minimum consecutive successes for the probe to be
                      considered successful after having failed.
                    format: int32
                    type: integer
                  tcpSocket:
                    description: Health is determined by if the proxy is able to connect.
                    properties:
                      host:
                        type: string
                      port:
                        maximum: 4294967295
                        minimum: 0
                        type: integer
                    required:
                    - port
                    type: object
                  timeoutSeconds:
                    description: Number of seconds after which the probe times out.
                    format: int32
                    type: integer
                type: object
              template:
                description: Template to be used for the generation of `WorkloadEntry`
                  resources that belong to this `WorkloadGroup`.
                properties:
                  address:
                    description: Address associated with the network endpoint without
                      the port.
                    maxLength: 256
                    type: string
                    x-kubernetes-validations:
                    - message: UDS must be an absolute path or abstract socket
                      rule: 'self.startsWith(''unix://'') ? (self.substring(7,8) ==
                        ''/'' || self.substring(7,8) == ''@'') : true'
                    - message: UDS may not be a dir
                      rule: 'self.startsWith(''unix://'') ? !self.endsWith(''/'')
                        : true'
                  labels:
                    additionalProperties:
                      type: string
                    description: One or more labels associated with the endpoint.
                    maxProperties: 256
                    type: object
                  locality:
                    description: The locality associated with the endpoint.
                    maxLength: 2048
                    type: string
                  network:
                    description: Network enables Istio to group endpoints resident
                      in the same L3 domain/network.
                    maxLength: 2048
                    type: string
                  ports:
                    additionalProperties:
                      maximum: 4294967295
                      minimum: 0
                      type: integer
                      x-kubernetes-validations:
                      - message: port must be between 1-65535
                        rule: 0 < self && self <= 65535
                    description: Set of ports associated with the endpoint.
                    maxProperties: 128
                    type: object
                    x-kubernetes-validations:
                    - message: port name must be valid
                      rule: self.all(key, size(key) < 63 && key.matches('^[a-zA-Z0-9](?:[-a-zA-Z0-9]*[a-zA-Z0-9])?$'))
                  serviceAccount:
                    description: The service account associated with the workload
                      if a sidecar is present in the workload.
                    maxLength: 253
                    type: string
                  weight:
                    description: The load balancing weight associated with the endpoint.
                    maximum: 4294967295
                    minimum: 0
                    type: integer
                type: object
                x-kubernetes-validations:
                - message: Address is required
                  rule: has(self.address) || has(self.network)
                - message: UDS may not include ports
                  rule: '(has(self.address) && self.address.startsWith(''unix://''))
                    ? !has(self.ports) : true'
            required:
            - template
            type: object
          status:
            type: object
            x-kubernetes-preserve-unknown-fields: true
        type: object
    served: true
    storage: false
    subresources:
      status: {}
  - additionalPrinterColumns:
    - description: 'CreationTimestamp is a timestamp representing the server time
        when this object was created. It is not guaranteed to be set in happens-before
        order across separate operations. Clients may not set this value. It is represented
        in RFC3339 form and is in UTC. Populated by the system. Read-only. Null for
        lists. More info: https://git.k8s.io/community/contributors/devel/api-conventions.md#metadata'
      jsonPath: .metadata.creationTimestamp
      name: Age
      type: date
    name: v1alpha3
    schema:
      openAPIV3Schema:
        properties:
          spec:
            description: 'Describes a collection of workload instances. See more details
              at: https://istio.io/docs/reference/config/networking/workload-group.html'
            properties:
              metadata:
                description: Metadata that will be used for all corresponding `WorkloadEntries`.
                properties:
                  annotations:
                    additionalProperties:
                      type: string
                    type: object
                  labels:
                    additionalProperties:
                      type: string
                    type: object
                type: object
              probe:
                description: '`ReadinessProbe` describes the configuration the user
                  must provide for healthchecking on their workload.'
                oneOf:
                - not:
                    anyOf:
                    - required:
                      - httpGet
                    - required:
                      - tcpSocket
                    - required:
                      - exec
                - required:
                  - httpGet
                - required:
                  - tcpSocket
                - required:
                  - exec
                properties:
                  exec:
                    description: Health is determined by how the command that is executed
                      exited.
                    properties:
                      command:
                        description: Command to run.
                        items:
                          type: string
                        type: array
                    type: object
                  failureThreshold:
                    description: Minimum consecutive failures for the probe to be
                      considered failed after having succeeded.
                    format: int32
                    type: integer
                  httpGet:
                    description: '`httpGet` is performed to a given endpoint and the
                      status/able to connect determines health.'
                    properties:
                      host:
                        description: Host name to connect to, defaults to the pod
                          IP.
                        type: string
                      httpHeaders:
                        description: Headers the proxy will pass on to make the request.
                        items:
                          properties:
                            name:
                              type: string
                            value:
                              type: string
                          type: object
                        type: array
                      path:
                        description: Path to access on the HTTP server.
                        type: string
                      port:
                        description: Port on which the endpoint lives.
                        maximum: 4294967295
                        minimum: 0
                        type: integer
                      scheme:
                        type: string
                    required:
                    - port
                    type: object
                  initialDelaySeconds:
                    description: Number of seconds after the container has started
                      before readiness probes are initiated.
                    format: int32
                    type: integer
                  periodSeconds:
                    description: How often (in seconds) to perform the probe.
                    format: int32
                    type: integer
                  successThreshold:
                    description: Minimum consecutive successes for the probe to be
                      considered successful after having failed.
                    format: int32
                    type: integer
                  tcpSocket:
                    description: Health is determined by if the proxy is able to connect.
                    properties:
                      host:
                        type: string
                      port:
                        maximum: 4294967295
                        minimum: 0
                        type: integer
                    required:
                    - port
                    type: object
                  timeoutSeconds:
                    description: Number of seconds after which the probe times out.
                    format: int32
                    type: integer
                type: object
              template:
                description: Template to be used for the generation of `WorkloadEntry`
                  resources that belong to this `WorkloadGroup`.
                properties:
                  address:
                    description: Address associated with the network endpoint without
                      the port.
                    maxLength: 256
                    type: string
                    x-kubernetes-validations:
                    - message: UDS must be an absolute path or abstract socket
                      rule: 'self.startsWith(''unix://'') ? (self.substring(7,8) ==
                        ''/'' || self.substring(7,8) == ''@'') : true'
                    - message: UDS may not be a dir
                      rule: 'self.startsWith(''unix://'') ? !self.endsWith(''/'')
                        : true'
                  labels:
                    additionalProperties:
                      type: string
                    description: One or more labels associated with the endpoint.
                    maxProperties: 256
                    type: object
                  locality:
                    description: The locality associated with the endpoint.
                    maxLength: 2048
                    type: string
                  network:
                    description: Network enables Istio to group endpoints resident
                      in the same L3 domain/network.
                    maxLength: 2048
                    type: string
                  ports:
                    additionalProperties:
                      maximum: 4294967295
                      minimum: 0
                      type: integer
                      x-kubernetes-validations:
                      - message: port must be between 1-65535
                        rule: 0 < self && self <= 65535
                    description: Set of ports associated with the endpoint.
                    maxProperties: 128
                    type: object
                    x-kubernetes-validations:
                    - message: port name must be valid
                      rule: self.all(key, size(key) < 63 && key.matches('^[a-zA-Z0-9](?:[-a-zA-Z0-9]*[a-zA-Z0-9])?$'))
                  serviceAccount:
                    description: The service account associated with the workload
                      if a sidecar is present in the workload.
                    maxLength: 253
                    type: string
                  weight:
                    description: The load balancing weight associated with the endpoint.
                    maximum: 4294967295
                    minimum: 0
                    type: integer
                type: object
                x-kubernetes-validations:
                - message: Address is required
                  rule: has(self.address) || has(self.network)
                - message: UDS may not include ports
                  rule: '(has(self.address) && self.address.startsWith(''unix://''))
                    ? !has(self.ports) : true'
            required:
            - template
            type: object
          status:
            type: object
            x-kubernetes-preserve-unknown-fields: true
        type: object
    served: true
    storage: false
    subresources:
      status: {}
  - additionalPrinterColumns:
    - description: 'CreationTimestamp is a timestamp representing the server time
        when this object was created. It is not guaranteed to be set in happens-before
        order across separate operations. Clients may not set this value. It is represented
        in RFC3339 form and is in UTC. Populated by the system. Read-only. Null for
        lists. More info: https://git.k8s.io/community/contributors/devel/api-conventions.md#metadata'
      jsonPath: .metadata.creationTimestamp
      name: Age
      type: date
    name: v1beta1
    schema:
      openAPIV3Schema:
        properties:
          spec:
            description: 'Describes a collection of workload instances. See more details
              at: https://istio.io/docs/reference/config/networking/workload-group.html'
            properties:
              metadata:
                description: Metadata that will be used for all corresponding `WorkloadEntries`.
                properties:
                  annotations:
                    additionalProperties:
                      type: string
                    type: object
                  labels:
                    additionalProperties:
                      type: string
                    type: object
                type: object
              probe:
                description: '`ReadinessProbe` describes the configuration the user
                  must provide for healthchecking on their workload.'
                oneOf:
                - not:
                    anyOf:
                    - required:
                      - httpGet
                    - required:
                      - tcpSocket
                    - required:
                      - exec
                - required:
                  - httpGet
                - required:
                  - tcpSocket
                - required:
                  - exec
                properties:
                  exec:
                    description: Health is determined by how the command that is executed
                      exited.
                    properties:
                      command:
                        description: Command to run.
                        items:
                          type: string
                        type: array
                    type: object
                  failureThreshold:
                    description: Minimum consecutive failures for the probe to be
                      considered failed after having succeeded.
                    format: int32
                    type: integer
                  httpGet:
                    description: '`httpGet` is performed to a given endpoint and the
                      status/able to connect determines health.'
                    properties:
                      host:
                        description: Host name to connect to, defaults to the pod
                          IP.
                        type: string
                      httpHeaders:
                        description: Headers the proxy will pass on to make the request.
                        items:
                          properties:
                            name:
                              type: string
                            value:
                              type: string
                          type: object
                        type: array
                      path:
                        description: Path to access on the HTTP server.
                        type: string
                      port:
                        description: Port on which the endpoint lives.
                        maximum: 4294967295
                        minimum: 0
                        type: integer
                      scheme:
                        type: string
                    required:
                    - port
                    type: object
                  initialDelaySeconds:
                    description: Number of seconds after the container has started
                      before readiness probes are initiated.
                    format: int32
                    type: integer
                  periodSeconds:
                    description: How often (in seconds) to perform the probe.
                    format: int32
                    type: integer
                  successThreshold:
                    description: Minimum consecutive successes for the probe to be
                      considered successful after having failed.
                    format: int32
                    type: integer
                  tcpSocket:
                    description: Health is determined by if the proxy is able to connect.
                    properties:
                      host:
                        type: string
                      port:
                        maximum: 4294967295
                        minimum: 0
                        type: integer
                    required:
                    - port
                    type: object
                  timeoutSeconds:
                    description: Number of seconds after which the probe times out.
                    format: int32
                    type: integer
                type: object
              template:
                description: Template to be used for the generation of `WorkloadEntry`
                  resources that belong to this `WorkloadGroup`.
                properties:
                  address:
                    description: Address associated with the network endpoint without
                      the port.
                    maxLength: 256
                    type: string
                    x-kubernetes-validations:
                    - message: UDS must be an absolute path or abstract socket
                      rule: 'self.startsWith(''unix://'') ? (self.substring(7,8) ==
                        ''/'' || self.substring(7,8) == ''@'') : true'
                    - message: UDS may not be a dir
                      rule: 'self.startsWith(''unix://'') ? !self.endsWith(''/'')
                        : true'
                  labels:
                    additionalProperties:
                      type: string
                    description: One or more labels associated with the endpoint.
                    maxProperties: 256
                    type: object
                  locality:
                    description: The locality associated with the endpoint.
                    maxLength: 2048
                    type: string
                  network:
                    description: Network enables Istio to group endpoints resident
                      in the same L3 domain/network.
                    maxLength: 2048
                    type: string
                  ports:
                    additionalProperties:
                      maximum: 4294967295
                      minimum: 0
                      type: integer
                      x-kubernetes-validations:
                      - message: port must be between 1-65535
                        rule: 0 < self && self <= 65535
                    description: Set of ports associated with the endpoint.
                    maxProperties: 128
                    type: object
                    x-kubernetes-validations:
                    - message: port name must be valid
                      rule: self.all(key, size(key) < 63 && key.matches('^[a-zA-Z0-9](?:[-a-zA-Z0-9]*[a-zA-Z0-9])?$'))
                  serviceAccount:
                    description: The service account associated with the workload
                      if a sidecar is present in the workload.
                    maxLength: 253
                    type: string
                  weight:
                    description: The load balancing weight associated with the endpoint.
                    maximum: 4294967295
                    minimum: 0
                    type: integer
                type: object
                x-kubernetes-validations:
                - message: Address is required
                  rule: has(self.address) || has(self.network)
                - message: UDS may not include ports
                  rule: '(has(self.address) && self.address.startsWith(''unix://''))
                    ? !has(self.ports) : true'
            required:
            - template
            type: object
          status:
            type: object
            x-kubernetes-preserve-unknown-fields: true
        type: object
    served: true
    storage: true
    subresources:
      status: {}
---
# Source: istio-microk8s/charts/base/templates/crds.yaml
apiVersion: apiextensions.k8s.io/v1
kind: CustomResourceDefinition
metadata:
  annotations:
    "helm.sh/resource-policy": keep
  labels:
    app: istio-pilot
    chart: istio
    heritage: Tiller
    istio: security
    release: istio
  name: authorizationpolicies.security.istio.io
spec:
  group: security.istio.io
  names:
    categories:
    - istio-io
    - security-istio-io
    kind: AuthorizationPolicy
    listKind: AuthorizationPolicyList
    plural: authorizationpolicies
    shortNames:
    - ap
    singular: authorizationpolicy
  scope: Namespaced
  versions:
  - additionalPrinterColumns:
    - description: The operation to take.
      jsonPath: .spec.action
      name: Action
      type: string
    - description: 'CreationTimestamp is a timestamp representing the server time
        when this object was created. It is not guaranteed to be set in happens-before
        order across separate operations. Clients may not set this value. It is represented
        in RFC3339 form and is in UTC. Populated by the system. Read-only. Null for
        lists. More info: https://git.k8s.io/community/contributors/devel/api-conventions.md#metadata'
      jsonPath: .metadata.creationTimestamp
      name: Age
      type: date
    name: v1
    schema:
      openAPIV3Schema:
        properties:
          spec:
            description: 'Configuration for access control on workloads. See more
              details at: https://istio.io/docs/reference/config/security/authorization-policy.html'
            oneOf:
            - not:
                anyOf:
                - required:
                  - provider
            - required:
              - provider
            properties:
              action:
                description: |-
                  Optional.

                  Valid Options: ALLOW, DENY, AUDIT, CUSTOM
                enum:
                - ALLOW
                - DENY
                - AUDIT
                - CUSTOM
                type: string
              provider:
                description: Specifies detailed configuration of the CUSTOM action.
                properties:
                  name:
                    description: Specifies the name of the extension provider.
                    type: string
                type: object
              rules:
                description: Optional.
                items:
                  properties:
                    from:
                      description: Optional.
                      items:
                        properties:
                          source:
                            description: Source specifies the source of a request.
                            properties:
                              ipBlocks:
                                description: Optional.
                                items:
                                  type: string
                                type: array
                              namespaces:
                                description: Optional.
                                items:
                                  type: string
                                type: array
                              notIpBlocks:
                                description: Optional.
                                items:
                                  type: string
                                type: array
                              notNamespaces:
                                description: Optional.
                                items:
                                  type: string
                                type: array
                              notPrincipals:
                                description: Optional.
                                items:
                                  type: string
                                type: array
                              notRemoteIpBlocks:
                                description: Optional.
                                items:
                                  type: string
                                type: array
                              notRequestPrincipals:
                                description: Optional.
                                items:
                                  type: string
                                type: array
                              principals:
                                description: Optional.
                                items:
                                  type: string
                                type: array
                              remoteIpBlocks:
                                description: Optional.
                                items:
                                  type: string
                                type: array
                              requestPrincipals:
                                description: Optional.
                                items:
                                  type: string
                                type: array
                            type: object
                        type: object
                      type: array
                    to:
                      description: Optional.
                      items:
                        properties:
                          operation:
                            description: Operation specifies the operation of a request.
                            properties:
                              hosts:
                                description: Optional.
                                items:
                                  type: string
                                type: array
                              methods:
                                description: Optional.
                                items:
                                  type: string
                                type: array
                              notHosts:
                                description: Optional.
                                items:
                                  type: string
                                type: array
                              notMethods:
                                description: Optional.
                                items:
                                  type: string
                                type: array
                              notPaths:
                                description: Optional.
                                items:
                                  type: string
                                type: array
                              notPorts:
                                description: Optional.
                                items:
                                  type: string
                                type: array
                              paths:
                                description: Optional.
                                items:
                                  type: string
                                type: array
                              ports:
                                description: Optional.
                                items:
                                  type: string
                                type: array
                            type: object
                        type: object
                      type: array
                    when:
                      description: Optional.
                      items:
                        properties:
                          key:
                            description: The name of an Istio attribute.
                            type: string
                          notValues:
                            description: Optional.
                            items:
                              type: string
                            type: array
                          values:
                            description: Optional.
                            items:
                              type: string
                            type: array
                        required:
                        - key
                        type: object
                      type: array
                  type: object
                type: array
              selector:
                description: Optional.
                properties:
                  matchLabels:
                    additionalProperties:
                      maxLength: 63
                      type: string
                      x-kubernetes-validations:
                      - message: wildcard not allowed in label value match
                        rule: '!self.contains(''*'')'
                    description: One or more labels that indicate a specific set of
                      pods/VMs on which a policy should be applied.
                    maxProperties: 4096
                    type: object
                    x-kubernetes-validations:
                    - message: wildcard not allowed in label key match
                      rule: self.all(key, !key.contains('*'))
                    - message: key must not be empty
                      rule: self.all(key, key.size() != 0)
                type: object
              targetRef:
                properties:
                  group:
                    description: group is the group of the target resource.
                    maxLength: 253
                    pattern: ^$|^[a-z0-9]([-a-z0-9]*[a-z0-9])?(\.[a-z0-9]([-a-z0-9]*[a-z0-9])?)*$
                    type: string
                  kind:
                    description: kind is kind of the target resource.
                    maxLength: 63
                    minLength: 1
                    pattern: ^[a-zA-Z]([-a-zA-Z0-9]*[a-zA-Z0-9])?$
                    type: string
                  name:
                    description: name is the name of the target resource.
                    maxLength: 253
                    minLength: 1
                    type: string
                  namespace:
                    description: namespace is the namespace of the referent.
                    type: string
                    x-kubernetes-validations:
                    - message: cross namespace referencing is not currently supported
                      rule: self.size() == 0
                required:
                - kind
                - name
                type: object
                x-kubernetes-validations:
                - message: Support kinds are core/Service and gateway.networking.k8s.io/Gateway
                  rule: '[self.group, self.kind] in [[''core'',''Service''], ['''',''Service''],
                    [''gateway.networking.k8s.io'',''Gateway'']]'
              targetRefs:
                description: Optional.
                items:
                  properties:
                    group:
                      description: group is the group of the target resource.
                      maxLength: 253
                      pattern: ^$|^[a-z0-9]([-a-z0-9]*[a-z0-9])?(\.[a-z0-9]([-a-z0-9]*[a-z0-9])?)*$
                      type: string
                    kind:
                      description: kind is kind of the target resource.
                      maxLength: 63
                      minLength: 1
                      pattern: ^[a-zA-Z]([-a-zA-Z0-9]*[a-zA-Z0-9])?$
                      type: string
                    name:
                      description: name is the name of the target resource.
                      maxLength: 253
                      minLength: 1
                      type: string
                    namespace:
                      description: namespace is the namespace of the referent.
                      type: string
                      x-kubernetes-validations:
                      - message: cross namespace referencing is not currently supported
                        rule: self.size() == 0
                  required:
                  - kind
                  - name
                  type: object
                  x-kubernetes-validations:
                  - message: Support kinds are core/Service and gateway.networking.k8s.io/Gateway
                    rule: '[self.group, self.kind] in [[''core'',''Service''], ['''',''Service''],
                      [''gateway.networking.k8s.io'',''Gateway'']]'
                type: array
            type: object
          status:
            type: object
            x-kubernetes-preserve-unknown-fields: true
        type: object
    served: true
    storage: false
    subresources:
      status: {}
  - additionalPrinterColumns:
    - description: The operation to take.
      jsonPath: .spec.action
      name: Action
      type: string
    - description: 'CreationTimestamp is a timestamp representing the server time
        when this object was created. It is not guaranteed to be set in happens-before
        order across separate operations. Clients may not set this value. It is represented
        in RFC3339 form and is in UTC. Populated by the system. Read-only. Null for
        lists. More info: https://git.k8s.io/community/contributors/devel/api-conventions.md#metadata'
      jsonPath: .metadata.creationTimestamp
      name: Age
      type: date
    name: v1beta1
    schema:
      openAPIV3Schema:
        properties:
          spec:
            description: 'Configuration for access control on workloads. See more
              details at: https://istio.io/docs/reference/config/security/authorization-policy.html'
            oneOf:
            - not:
                anyOf:
                - required:
                  - provider
            - required:
              - provider
            properties:
              action:
                description: |-
                  Optional.

                  Valid Options: ALLOW, DENY, AUDIT, CUSTOM
                enum:
                - ALLOW
                - DENY
                - AUDIT
                - CUSTOM
                type: string
              provider:
                description: Specifies detailed configuration of the CUSTOM action.
                properties:
                  name:
                    description: Specifies the name of the extension provider.
                    type: string
                type: object
              rules:
                description: Optional.
                items:
                  properties:
                    from:
                      description: Optional.
                      items:
                        properties:
                          source:
                            description: Source specifies the source of a request.
                            properties:
                              ipBlocks:
                                description: Optional.
                                items:
                                  type: string
                                type: array
                              namespaces:
                                description: Optional.
                                items:
                                  type: string
                                type: array
                              notIpBlocks:
                                description: Optional.
                                items:
                                  type: string
                                type: array
                              notNamespaces:
                                description: Optional.
                                items:
                                  type: string
                                type: array
                              notPrincipals:
                                description: Optional.
                                items:
                                  type: string
                                type: array
                              notRemoteIpBlocks:
                                description: Optional.
                                items:
                                  type: string
                                type: array
                              notRequestPrincipals:
                                description: Optional.
                                items:
                                  type: string
                                type: array
                              principals:
                                description: Optional.
                                items:
                                  type: string
                                type: array
                              remoteIpBlocks:
                                description: Optional.
                                items:
                                  type: string
                                type: array
                              requestPrincipals:
                                description: Optional.
                                items:
                                  type: string
                                type: array
                            type: object
                        type: object
                      type: array
                    to:
                      description: Optional.
                      items:
                        properties:
                          operation:
                            description: Operation specifies the operation of a request.
                            properties:
                              hosts:
                                description: Optional.
                                items:
                                  type: string
                                type: array
                              methods:
                                description: Optional.
                                items:
                                  type: string
                                type: array
                              notHosts:
                                description: Optional.
                                items:
                                  type: string
                                type: array
                              notMethods:
                                description: Optional.
                                items:
                                  type: string
                                type: array
                              notPaths:
                                description: Optional.
                                items:
                                  type: string
                                type: array
                              notPorts:
                                description: Optional.
                                items:
                                  type: string
                                type: array
                              paths:
                                description: Optional.
                                items:
                                  type: string
                                type: array
                              ports:
                                description: Optional.
                                items:
                                  type: string
                                type: array
                            type: object
                        type: object
                      type: array
                    when:
                      description: Optional.
                      items:
                        properties:
                          key:
                            description: The name of an Istio attribute.
                            type: string
                          notValues:
                            description: Optional.
                            items:
                              type: string
                            type: array
                          values:
                            description: Optional.
                            items:
                              type: string
                            type: array
                        required:
                        - key
                        type: object
                      type: array
                  type: object
                type: array
              selector:
                description: Optional.
                properties:
                  matchLabels:
                    additionalProperties:
                      maxLength: 63
                      type: string
                      x-kubernetes-validations:
                      - message: wildcard not allowed in label value match
                        rule: '!self.contains(''*'')'
                    description: One or more labels that indicate a specific set of
                      pods/VMs on which a policy should be applied.
                    maxProperties: 4096
                    type: object
                    x-kubernetes-validations:
                    - message: wildcard not allowed in label key match
                      rule: self.all(key, !key.contains('*'))
                    - message: key must not be empty
                      rule: self.all(key, key.size() != 0)
                type: object
              targetRef:
                properties:
                  group:
                    description: group is the group of the target resource.
                    maxLength: 253
                    pattern: ^$|^[a-z0-9]([-a-z0-9]*[a-z0-9])?(\.[a-z0-9]([-a-z0-9]*[a-z0-9])?)*$
                    type: string
                  kind:
                    description: kind is kind of the target resource.
                    maxLength: 63
                    minLength: 1
                    pattern: ^[a-zA-Z]([-a-zA-Z0-9]*[a-zA-Z0-9])?$
                    type: string
                  name:
                    description: name is the name of the target resource.
                    maxLength: 253
                    minLength: 1
                    type: string
                  namespace:
                    description: namespace is the namespace of the referent.
                    type: string
                    x-kubernetes-validations:
                    - message: cross namespace referencing is not currently supported
                      rule: self.size() == 0
                required:
                - kind
                - name
                type: object
                x-kubernetes-validations:
                - message: Support kinds are core/Service and gateway.networking.k8s.io/Gateway
                  rule: '[self.group, self.kind] in [[''core'',''Service''], ['''',''Service''],
                    [''gateway.networking.k8s.io'',''Gateway'']]'
              targetRefs:
                description: Optional.
                items:
                  properties:
                    group:
                      description: group is the group of the target resource.
                      maxLength: 253
                      pattern: ^$|^[a-z0-9]([-a-z0-9]*[a-z0-9])?(\.[a-z0-9]([-a-z0-9]*[a-z0-9])?)*$
                      type: string
                    kind:
                      description: kind is kind of the target resource.
                      maxLength: 63
                      minLength: 1
                      pattern: ^[a-zA-Z]([-a-zA-Z0-9]*[a-zA-Z0-9])?$
                      type: string
                    name:
                      description: name is the name of the target resource.
                      maxLength: 253
                      minLength: 1
                      type: string
                    namespace:
                      description: namespace is the namespace of the referent.
                      type: string
                      x-kubernetes-validations:
                      - message: cross namespace referencing is not currently supported
                        rule: self.size() == 0
                  required:
                  - kind
                  - name
                  type: object
                  x-kubernetes-validations:
                  - message: Support kinds are core/Service and gateway.networking.k8s.io/Gateway
                    rule: '[self.group, self.kind] in [[''core'',''Service''], ['''',''Service''],
                      [''gateway.networking.k8s.io'',''Gateway'']]'
                type: array
            type: object
          status:
            type: object
            x-kubernetes-preserve-unknown-fields: true
        type: object
    served: true
    storage: true
    subresources:
      status: {}
---
# Source: istio-microk8s/charts/base/templates/crds.yaml
apiVersion: apiextensions.k8s.io/v1
kind: CustomResourceDefinition
metadata:
  annotations:
    "helm.sh/resource-policy": keep
  labels:
    app: istio-pilot
    chart: istio
    heritage: Tiller
    istio: security
    release: istio
  name: peerauthentications.security.istio.io
spec:
  group: security.istio.io
  names:
    categories:
    - istio-io
    - security-istio-io
    kind: PeerAuthentication
    listKind: PeerAuthenticationList
    plural: peerauthentications
    shortNames:
    - pa
    singular: peerauthentication
  scope: Namespaced
  versions:
  - additionalPrinterColumns:
    - description: Defines the mTLS mode used for peer authentication.
      jsonPath: .spec.mtls.mode
      name: Mode
      type: string
    - description: 'CreationTimestamp is a timestamp representing the server time
        when this object was created. It is not guaranteed to be set in happens-before
        order across separate operations. Clients may not set this value. It is represented
        in RFC3339 form and is in UTC. Populated by the system. Read-only. Null for
        lists. More info: https://git.k8s.io/community/contributors/devel/api-conventions.md#metadata'
      jsonPath: .metadata.creationTimestamp
      name: Age
      type: date
    name: v1
    schema:
      openAPIV3Schema:
        properties:
          spec:
            description: 'Peer authentication configuration for workloads. See more
              details at: https://istio.io/docs/reference/config/security/peer_authentication.html'
            properties:
              mtls:
                description: Mutual TLS settings for workload.
                properties:
                  mode:
                    description: |-
                      Defines the mTLS mode used for peer authentication.

                      Valid Options: DISABLE, PERMISSIVE, STRICT
                    enum:
                    - UNSET
                    - DISABLE
                    - PERMISSIVE
                    - STRICT
                    type: string
                type: object
              portLevelMtls:
                additionalProperties:
                  properties:
                    mode:
                      description: |-
                        Defines the mTLS mode used for peer authentication.

                        Valid Options: DISABLE, PERMISSIVE, STRICT
                      enum:
                      - UNSET
                      - DISABLE
                      - PERMISSIVE
                      - STRICT
                      type: string
                  type: object
                description: Port specific mutual TLS settings.
                minProperties: 1
                type: object
                x-kubernetes-validations:
                - message: port must be between 1-65535
                  rule: self.all(key, 0 < int(key) && int(key) <= 65535)
              selector:
                description: The selector determines the workloads to apply the PeerAuthentication
                  on.
                properties:
                  matchLabels:
                    additionalProperties:
                      maxLength: 63
                      type: string
                      x-kubernetes-validations:
                      - message: wildcard not allowed in label value match
                        rule: '!self.contains(''*'')'
                    description: One or more labels that indicate a specific set of
                      pods/VMs on which a policy should be applied.
                    maxProperties: 4096
                    type: object
                    x-kubernetes-validations:
                    - message: wildcard not allowed in label key match
                      rule: self.all(key, !key.contains('*'))
                    - message: key must not be empty
                      rule: self.all(key, key.size() != 0)
                type: object
            type: object
            x-kubernetes-validations:
            - message: portLevelMtls requires selector
              rule: (has(self.selector) && has(self.selector.matchLabels) && self.selector.matchLabels.size()
                > 0) || !has(self.portLevelMtls)
          status:
            type: object
            x-kubernetes-preserve-unknown-fields: true
        type: object
    served: true
    storage: false
    subresources:
      status: {}
  - additionalPrinterColumns:
    - description: Defines the mTLS mode used for peer authentication.
      jsonPath: .spec.mtls.mode
      name: Mode
      type: string
    - description: 'CreationTimestamp is a timestamp representing the server time
        when this object was created. It is not guaranteed to be set in happens-before
        order across separate operations. Clients may not set this value. It is represented
        in RFC3339 form and is in UTC. Populated by the system. Read-only. Null for
        lists. More info: https://git.k8s.io/community/contributors/devel/api-conventions.md#metadata'
      jsonPath: .metadata.creationTimestamp
      name: Age
      type: date
    name: v1beta1
    schema:
      openAPIV3Schema:
        properties:
          spec:
            description: 'Peer authentication configuration for workloads. See more
              details at: https://istio.io/docs/reference/config/security/peer_authentication.html'
            properties:
              mtls:
                description: Mutual TLS settings for workload.
                properties:
                  mode:
                    description: |-
                      Defines the mTLS mode used for peer authentication.

                      Valid Options: DISABLE, PERMISSIVE, STRICT
                    enum:
                    - UNSET
                    - DISABLE
                    - PERMISSIVE
                    - STRICT
                    type: string
                type: object
              portLevelMtls:
                additionalProperties:
                  properties:
                    mode:
                      description: |-
                        Defines the mTLS mode used for peer authentication.

                        Valid Options: DISABLE, PERMISSIVE, STRICT
                      enum:
                      - UNSET
                      - DISABLE
                      - PERMISSIVE
                      - STRICT
                      type: string
                  type: object
                description: Port specific mutual TLS settings.
                minProperties: 1
                type: object
                x-kubernetes-validations:
                - message: port must be between 1-65535
                  rule: self.all(key, 0 < int(key) && int(key) <= 65535)
              selector:
                description: The selector determines the workloads to apply the PeerAuthentication
                  on.
                properties:
                  matchLabels:
                    additionalProperties:
                      maxLength: 63
                      type: string
                      x-kubernetes-validations:
                      - message: wildcard not allowed in label value match
                        rule: '!self.contains(''*'')'
                    description: One or more labels that indicate a specific set of
                      pods/VMs on which a policy should be applied.
                    maxProperties: 4096
                    type: object
                    x-kubernetes-validations:
                    - message: wildcard not allowed in label key match
                      rule: self.all(key, !key.contains('*'))
                    - message: key must not be empty
                      rule: self.all(key, key.size() != 0)
                type: object
            type: object
            x-kubernetes-validations:
            - message: portLevelMtls requires selector
              rule: (has(self.selector) && has(self.selector.matchLabels) && self.selector.matchLabels.size()
                > 0) || !has(self.portLevelMtls)
          status:
            type: object
            x-kubernetes-preserve-unknown-fields: true
        type: object
    served: true
    storage: true
    subresources:
      status: {}
---
# Source: istio-microk8s/charts/base/templates/crds.yaml
apiVersion: apiextensions.k8s.io/v1
kind: CustomResourceDefinition
metadata:
  annotations:
    "helm.sh/resource-policy": keep
  labels:
    app: istio-pilot
    chart: istio
    heritage: Tiller
    istio: security
    release: istio
  name: requestauthentications.security.istio.io
spec:
  group: security.istio.io
  names:
    categories:
    - istio-io
    - security-istio-io
    kind: RequestAuthentication
    listKind: RequestAuthenticationList
    plural: requestauthentications
    shortNames:
    - ra
    singular: requestauthentication
  scope: Namespaced
  versions:
  - name: v1
    schema:
      openAPIV3Schema:
        properties:
          spec:
            description: 'Request authentication configuration for workloads. See
              more details at: https://istio.io/docs/reference/config/security/request_authentication.html'
            properties:
              jwtRules:
                description: Define the list of JWTs that can be validated at the
                  selected workloads' proxy.
                items:
                  properties:
                    audiences:
                      description: The list of JWT [audiences](https://tools.ietf.org/html/rfc7519#section-4.1.3)
                        that are allowed to access.
                      items:
                        minLength: 1
                        type: string
                      type: array
                    forwardOriginalToken:
                      description: If set to true, the original token will be kept
                        for the upstream request.
                      type: boolean
                    fromCookies:
                      description: List of cookie names from which JWT is expected.
                      items:
                        minLength: 1
                        type: string
                      type: array
                    fromHeaders:
                      description: List of header locations from which JWT is expected.
                      items:
                        properties:
                          name:
                            description: The HTTP header name.
                            minLength: 1
                            type: string
                          prefix:
                            description: The prefix that should be stripped before
                              decoding the token.
                            type: string
                        required:
                        - name
                        type: object
                      type: array
                    fromParams:
                      description: List of query parameters from which JWT is expected.
                      items:
                        minLength: 1
                        type: string
                      type: array
                    issuer:
                      description: Identifies the issuer that issued the JWT.
                      minLength: 1
                      type: string
                    jwks:
                      description: JSON Web Key Set of public keys to validate signature
                        of the JWT.
                      type: string
                    jwks_uri:
                      description: URL of the provider's public key set to validate
                        signature of the JWT.
                      maxLength: 2048
                      minLength: 1
                      type: string
                      x-kubernetes-validations:
                      - message: url must have scheme http:// or https://
                        rule: url(self).getScheme() in ['http', 'https']
                    jwksUri:
                      description: URL of the provider's public key set to validate
                        signature of the JWT.
                      maxLength: 2048
                      minLength: 1
                      type: string
                      x-kubernetes-validations:
                      - message: url must have scheme http:// or https://
                        rule: url(self).getScheme() in ['http', 'https']
                    outputClaimToHeaders:
                      description: This field specifies a list of operations to copy
                        the claim to HTTP headers on a successfully verified token.
                      items:
                        properties:
                          claim:
                            description: The name of the claim to be copied from.
                            minLength: 1
                            type: string
                          header:
                            description: The name of the header to be created.
                            minLength: 1
                            pattern: ^[-_A-Za-z0-9]+$
                            type: string
                        required:
                        - header
                        - claim
                        type: object
                      type: array
                    outputPayloadToHeader:
                      description: This field specifies the header name to output
                        a successfully verified JWT payload to the backend.
                      type: string
                    timeout:
                      description: The maximum amount of time that the resolver, determined
                        by the PILOT_JWT_ENABLE_REMOTE_JWKS environment variable,
                        will spend waiting for the JWKS to be fetched.
                      type: string
                      x-kubernetes-validations:
                      - message: must be a valid duration greater than 1ms
                        rule: duration(self) >= duration('1ms')
                  required:
                  - issuer
                  type: object
                  x-kubernetes-validations:
                  - message: only one of jwks or jwksUri can be set
                    rule: (has(self.jwksUri)?1:0)+(has(self.jwks_uri)?1:0)+(has(self.jwks)?1:0)<=1
                maxItems: 4096
                type: array
              selector:
                description: Optional.
                properties:
                  matchLabels:
                    additionalProperties:
                      maxLength: 63
                      type: string
                      x-kubernetes-validations:
                      - message: wildcard not allowed in label value match
                        rule: '!self.contains(''*'')'
                    description: One or more labels that indicate a specific set of
                      pods/VMs on which a policy should be applied.
                    maxProperties: 4096
                    type: object
                    x-kubernetes-validations:
                    - message: wildcard not allowed in label key match
                      rule: self.all(key, !key.contains('*'))
                    - message: key must not be empty
                      rule: self.all(key, key.size() != 0)
                type: object
              targetRef:
                properties:
                  group:
                    description: group is the group of the target resource.
                    maxLength: 253
                    pattern: ^$|^[a-z0-9]([-a-z0-9]*[a-z0-9])?(\.[a-z0-9]([-a-z0-9]*[a-z0-9])?)*$
                    type: string
                  kind:
                    description: kind is kind of the target resource.
                    maxLength: 63
                    minLength: 1
                    pattern: ^[a-zA-Z]([-a-zA-Z0-9]*[a-zA-Z0-9])?$
                    type: string
                  name:
                    description: name is the name of the target resource.
                    maxLength: 253
                    minLength: 1
                    type: string
                  namespace:
                    description: namespace is the namespace of the referent.
                    type: string
                    x-kubernetes-validations:
                    - message: cross namespace referencing is not currently supported
                      rule: self.size() == 0
                required:
                - kind
                - name
                type: object
                x-kubernetes-validations:
                - message: Support kinds are core/Service and gateway.networking.k8s.io/Gateway
                  rule: '[self.group, self.kind] in [[''core'',''Service''], ['''',''Service''],
                    [''gateway.networking.k8s.io'',''Gateway'']]'
              targetRefs:
                description: Optional.
                items:
                  properties:
                    group:
                      description: group is the group of the target resource.
                      maxLength: 253
                      pattern: ^$|^[a-z0-9]([-a-z0-9]*[a-z0-9])?(\.[a-z0-9]([-a-z0-9]*[a-z0-9])?)*$
                      type: string
                    kind:
                      description: kind is kind of the target resource.
                      maxLength: 63
                      minLength: 1
                      pattern: ^[a-zA-Z]([-a-zA-Z0-9]*[a-zA-Z0-9])?$
                      type: string
                    name:
                      description: name is the name of the target resource.
                      maxLength: 253
                      minLength: 1
                      type: string
                    namespace:
                      description: namespace is the namespace of the referent.
                      type: string
                      x-kubernetes-validations:
                      - message: cross namespace referencing is not currently supported
                        rule: self.size() == 0
                  required:
                  - kind
                  - name
                  type: object
                  x-kubernetes-validations:
                  - message: Support kinds are core/Service and gateway.networking.k8s.io/Gateway
                    rule: '[self.group, self.kind] in [[''core'',''Service''], ['''',''Service''],
                      [''gateway.networking.k8s.io'',''Gateway'']]'
                type: array
            type: object
            x-kubernetes-validations:
            - message: only one of targetRefs or workloadSelector can be set
              rule: (has(self.selector)?1:0)+(has(self.targetRef)?1:0)+(has(self.targetRefs)?1:0)<=1
          status:
            type: object
            x-kubernetes-preserve-unknown-fields: true
        type: object
    served: true
    storage: false
    subresources:
      status: {}
  - name: v1beta1
    schema:
      openAPIV3Schema:
        properties:
          spec:
            description: 'Request authentication configuration for workloads. See
              more details at: https://istio.io/docs/reference/config/security/request_authentication.html'
            properties:
              jwtRules:
                description: Define the list of JWTs that can be validated at the
                  selected workloads' proxy.
                items:
                  properties:
                    audiences:
                      description: The list of JWT [audiences](https://tools.ietf.org/html/rfc7519#section-4.1.3)
                        that are allowed to access.
                      items:
                        minLength: 1
                        type: string
                      type: array
                    forwardOriginalToken:
                      description: If set to true, the original token will be kept
                        for the upstream request.
                      type: boolean
                    fromCookies:
                      description: List of cookie names from which JWT is expected.
                      items:
                        minLength: 1
                        type: string
                      type: array
                    fromHeaders:
                      description: List of header locations from which JWT is expected.
                      items:
                        properties:
                          name:
                            description: The HTTP header name.
                            minLength: 1
                            type: string
                          prefix:
                            description: The prefix that should be stripped before
                              decoding the token.
                            type: string
                        required:
                        - name
                        type: object
                      type: array
                    fromParams:
                      description: List of query parameters from which JWT is expected.
                      items:
                        minLength: 1
                        type: string
                      type: array
                    issuer:
                      description: Identifies the issuer that issued the JWT.
                      minLength: 1
                      type: string
                    jwks:
                      description: JSON Web Key Set of public keys to validate signature
                        of the JWT.
                      type: string
                    jwks_uri:
                      description: URL of the provider's public key set to validate
                        signature of the JWT.
                      maxLength: 2048
                      minLength: 1
                      type: string
                      x-kubernetes-validations:
                      - message: url must have scheme http:// or https://
                        rule: url(self).getScheme() in ['http', 'https']
                    jwksUri:
                      description: URL of the provider's public key set to validate
                        signature of the JWT.
                      maxLength: 2048
                      minLength: 1
                      type: string
                      x-kubernetes-validations:
                      - message: url must have scheme http:// or https://
                        rule: url(self).getScheme() in ['http', 'https']
                    outputClaimToHeaders:
                      description: This field specifies a list of operations to copy
                        the claim to HTTP headers on a successfully verified token.
                      items:
                        properties:
                          claim:
                            description: The name of the claim to be copied from.
                            minLength: 1
                            type: string
                          header:
                            description: The name of the header to be created.
                            minLength: 1
                            pattern: ^[-_A-Za-z0-9]+$
                            type: string
                        required:
                        - header
                        - claim
                        type: object
                      type: array
                    outputPayloadToHeader:
                      description: This field specifies the header name to output
                        a successfully verified JWT payload to the backend.
                      type: string
                    timeout:
                      description: The maximum amount of time that the resolver, determined
                        by the PILOT_JWT_ENABLE_REMOTE_JWKS environment variable,
                        will spend waiting for the JWKS to be fetched.
                      type: string
                      x-kubernetes-validations:
                      - message: must be a valid duration greater than 1ms
                        rule: duration(self) >= duration('1ms')
                  required:
                  - issuer
                  type: object
                  x-kubernetes-validations:
                  - message: only one of jwks or jwksUri can be set
                    rule: (has(self.jwksUri)?1:0)+(has(self.jwks_uri)?1:0)+(has(self.jwks)?1:0)<=1
                maxItems: 4096
                type: array
              selector:
                description: Optional.
                properties:
                  matchLabels:
                    additionalProperties:
                      maxLength: 63
                      type: string
                      x-kubernetes-validations:
                      - message: wildcard not allowed in label value match
                        rule: '!self.contains(''*'')'
                    description: One or more labels that indicate a specific set of
                      pods/VMs on which a policy should be applied.
                    maxProperties: 4096
                    type: object
                    x-kubernetes-validations:
                    - message: wildcard not allowed in label key match
                      rule: self.all(key, !key.contains('*'))
                    - message: key must not be empty
                      rule: self.all(key, key.size() != 0)
                type: object
              targetRef:
                properties:
                  group:
                    description: group is the group of the target resource.
                    maxLength: 253
                    pattern: ^$|^[a-z0-9]([-a-z0-9]*[a-z0-9])?(\.[a-z0-9]([-a-z0-9]*[a-z0-9])?)*$
                    type: string
                  kind:
                    description: kind is kind of the target resource.
                    maxLength: 63
                    minLength: 1
                    pattern: ^[a-zA-Z]([-a-zA-Z0-9]*[a-zA-Z0-9])?$
                    type: string
                  name:
                    description: name is the name of the target resource.
                    maxLength: 253
                    minLength: 1
                    type: string
                  namespace:
                    description: namespace is the namespace of the referent.
                    type: string
                    x-kubernetes-validations:
                    - message: cross namespace referencing is not currently supported
                      rule: self.size() == 0
                required:
                - kind
                - name
                type: object
                x-kubernetes-validations:
                - message: Support kinds are core/Service and gateway.networking.k8s.io/Gateway
                  rule: '[self.group, self.kind] in [[''core'',''Service''], ['''',''Service''],
                    [''gateway.networking.k8s.io'',''Gateway'']]'
              targetRefs:
                description: Optional.
                items:
                  properties:
                    group:
                      description: group is the group of the target resource.
                      maxLength: 253
                      pattern: ^$|^[a-z0-9]([-a-z0-9]*[a-z0-9])?(\.[a-z0-9]([-a-z0-9]*[a-z0-9])?)*$
                      type: string
                    kind:
                      description: kind is kind of the target resource.
                      maxLength: 63
                      minLength: 1
                      pattern: ^[a-zA-Z]([-a-zA-Z0-9]*[a-zA-Z0-9])?$
                      type: string
                    name:
                      description: name is the name of the target resource.
                      maxLength: 253
                      minLength: 1
                      type: string
                    namespace:
                      description: namespace is the namespace of the referent.
                      type: string
                      x-kubernetes-validations:
                      - message: cross namespace referencing is not currently supported
                        rule: self.size() == 0
                  required:
                  - kind
                  - name
                  type: object
                  x-kubernetes-validations:
                  - message: Support kinds are core/Service and gateway.networking.k8s.io/Gateway
                    rule: '[self.group, self.kind] in [[''core'',''Service''], ['''',''Service''],
                      [''gateway.networking.k8s.io'',''Gateway'']]'
                type: array
            type: object
            x-kubernetes-validations:
            - message: only one of targetRefs or workloadSelector can be set
              rule: (has(self.selector)?1:0)+(has(self.targetRef)?1:0)+(has(self.targetRefs)?1:0)<=1
          status:
            type: object
            x-kubernetes-preserve-unknown-fields: true
        type: object
    served: true
    storage: true
    subresources:
      status: {}
---
# Source: istio-microk8s/charts/base/templates/crds.yaml
apiVersion: apiextensions.k8s.io/v1
kind: CustomResourceDefinition
metadata:
  annotations:
    "helm.sh/resource-policy": keep
  labels:
    app: istio-pilot
    chart: istio
    heritage: Tiller
    istio: telemetry
    release: istio
  name: telemetries.telemetry.istio.io
spec:
  group: telemetry.istio.io
  names:
    categories:
    - istio-io
    - telemetry-istio-io
    kind: Telemetry
    listKind: TelemetryList
    plural: telemetries
    shortNames:
    - telemetry
    singular: telemetry
  scope: Namespaced
  versions:
  - additionalPrinterColumns:
    - description: 'CreationTimestamp is a timestamp representing the server time
        when this object was created. It is not guaranteed to be set in happens-before
        order across separate operations. Clients may not set this value. It is represented
        in RFC3339 form and is in UTC. Populated by the system. Read-only. Null for
        lists. More info: https://git.k8s.io/community/contributors/devel/api-conventions.md#metadata'
      jsonPath: .metadata.creationTimestamp
      name: Age
      type: date
    name: v1
    schema:
      openAPIV3Schema:
        properties:
          spec:
            description: 'Telemetry configuration for workloads. See more details
              at: https://istio.io/docs/reference/config/telemetry.html'
            properties:
              accessLogging:
                description: Optional.
                items:
                  properties:
                    disabled:
                      description: Controls logging.
                      nullable: true
                      type: boolean
                    filter:
                      description: Optional.
                      properties:
                        expression:
                          description: CEL expression for selecting when requests/connections
                            should be logged.
                          type: string
                      type: object
                    match:
                      description: Allows tailoring of logging behavior to specific
                        conditions.
                      properties:
                        mode:
                          description: |-
                            This determines whether or not to apply the access logging configuration based on the direction of traffic relative to the proxied workload.

                            Valid Options: CLIENT_AND_SERVER, CLIENT, SERVER
                          enum:
                          - CLIENT_AND_SERVER
                          - CLIENT
                          - SERVER
                          type: string
                      type: object
                    providers:
                      description: Optional.
                      items:
                        properties:
                          name:
                            description: Required.
                            minLength: 1
                            type: string
                        required:
                        - name
                        type: object
                      type: array
                  type: object
                type: array
              metrics:
                description: Optional.
                items:
                  properties:
                    overrides:
                      description: Optional.
                      items:
                        properties:
                          disabled:
                            description: Optional.
                            nullable: true
                            type: boolean
                          match:
                            description: Match allows providing the scope of the override.
                            oneOf:
                            - not:
                                anyOf:
                                - required:
                                  - metric
                                - required:
                                  - customMetric
                            - required:
                              - metric
                            - required:
                              - customMetric
                            properties:
                              customMetric:
                                description: Allows free-form specification of a metric.
                                minLength: 1
                                type: string
                              metric:
                                description: |-
                                  One of the well-known [Istio Standard Metrics](https://istio.io/latest/docs/reference/config/metrics/).

                                  Valid Options: ALL_METRICS, REQUEST_COUNT, REQUEST_DURATION, REQUEST_SIZE, RESPONSE_SIZE, TCP_OPENED_CONNECTIONS, TCP_CLOSED_CONNECTIONS, TCP_SENT_BYTES, TCP_RECEIVED_BYTES, GRPC_REQUEST_MESSAGES, GRPC_RESPONSE_MESSAGES
                                enum:
                                - ALL_METRICS
                                - REQUEST_COUNT
                                - REQUEST_DURATION
                                - REQUEST_SIZE
                                - RESPONSE_SIZE
                                - TCP_OPENED_CONNECTIONS
                                - TCP_CLOSED_CONNECTIONS
                                - TCP_SENT_BYTES
                                - TCP_RECEIVED_BYTES
                                - GRPC_REQUEST_MESSAGES
                                - GRPC_RESPONSE_MESSAGES
                                type: string
                              mode:
                                description: |-
                                  Controls which mode of metrics generation is selected: `CLIENT`, `SERVER`, or `CLIENT_AND_SERVER`.

                                  Valid Options: CLIENT_AND_SERVER, CLIENT, SERVER
                                enum:
                                - CLIENT_AND_SERVER
                                - CLIENT
                                - SERVER
                                type: string
                            type: object
                          tagOverrides:
                            additionalProperties:
                              properties:
                                operation:
                                  description: |-
                                    Operation controls whether or not to update/add a tag, or to remove it.

                                    Valid Options: UPSERT, REMOVE
                                  enum:
                                  - UPSERT
                                  - REMOVE
                                  type: string
                                value:
                                  description: Value is only considered if the operation
                                    is `UPSERT`.
                                  type: string
                              type: object
                              x-kubernetes-validations:
                              - message: value must be set when operation is UPSERT
                                rule: '((has(self.operation) ? self.operation : '''')
                                  == ''UPSERT'') ? self.value != '''' : true'
                              - message: value must not be set when operation is REMOVE
                                rule: '((has(self.operation) ? self.operation : '''')
                                  == ''REMOVE'') ? !has(self.value) : true'
                            description: Optional.
                            type: object
                        type: object
                      type: array
                    providers:
                      description: Optional.
                      items:
                        properties:
                          name:
                            description: Required.
                            minLength: 1
                            type: string
                        required:
                        - name
                        type: object
                      type: array
                    reportingInterval:
                      description: Optional.
                      type: string
                      x-kubernetes-validations:
                      - message: must be a valid duration greater than 1ms
                        rule: duration(self) >= duration('1ms')
                  type: object
                type: array
              selector:
                description: Optional.
                properties:
                  matchLabels:
                    additionalProperties:
                      maxLength: 63
                      type: string
                      x-kubernetes-validations:
                      - message: wildcard not allowed in label value match
                        rule: '!self.contains(''*'')'
                    description: One or more labels that indicate a specific set of
                      pods/VMs on which a policy should be applied.
                    maxProperties: 4096
                    type: object
                    x-kubernetes-validations:
                    - message: wildcard not allowed in label key match
                      rule: self.all(key, !key.contains('*'))
                    - message: key must not be empty
                      rule: self.all(key, key.size() != 0)
                type: object
              targetRef:
                properties:
                  group:
                    description: group is the group of the target resource.
                    maxLength: 253
                    pattern: ^$|^[a-z0-9]([-a-z0-9]*[a-z0-9])?(\.[a-z0-9]([-a-z0-9]*[a-z0-9])?)*$
                    type: string
                  kind:
                    description: kind is kind of the target resource.
                    maxLength: 63
                    minLength: 1
                    pattern: ^[a-zA-Z]([-a-zA-Z0-9]*[a-zA-Z0-9])?$
                    type: string
                  name:
                    description: name is the name of the target resource.
                    maxLength: 253
                    minLength: 1
                    type: string
                  namespace:
                    description: namespace is the namespace of the referent.
                    type: string
                    x-kubernetes-validations:
                    - message: cross namespace referencing is not currently supported
                      rule: self.size() == 0
                required:
                - kind
                - name
                type: object
                x-kubernetes-validations:
                - message: Support kinds are core/Service and gateway.networking.k8s.io/Gateway
                  rule: '[self.group, self.kind] in [[''core'',''Service''], ['''',''Service''],
                    [''gateway.networking.k8s.io'',''Gateway'']]'
              targetRefs:
                description: Optional.
                items:
                  properties:
                    group:
                      description: group is the group of the target resource.
                      maxLength: 253
                      pattern: ^$|^[a-z0-9]([-a-z0-9]*[a-z0-9])?(\.[a-z0-9]([-a-z0-9]*[a-z0-9])?)*$
                      type: string
                    kind:
                      description: kind is kind of the target resource.
                      maxLength: 63
                      minLength: 1
                      pattern: ^[a-zA-Z]([-a-zA-Z0-9]*[a-zA-Z0-9])?$
                      type: string
                    name:
                      description: name is the name of the target resource.
                      maxLength: 253
                      minLength: 1
                      type: string
                    namespace:
                      description: namespace is the namespace of the referent.
                      type: string
                      x-kubernetes-validations:
                      - message: cross namespace referencing is not currently supported
                        rule: self.size() == 0
                  required:
                  - kind
                  - name
                  type: object
                  x-kubernetes-validations:
                  - message: Support kinds are core/Service and gateway.networking.k8s.io/Gateway
                    rule: '[self.group, self.kind] in [[''core'',''Service''], ['''',''Service''],
                      [''gateway.networking.k8s.io'',''Gateway'']]'
                type: array
              tracing:
                description: Optional.
                items:
                  properties:
                    customTags:
                      additionalProperties:
                        oneOf:
                        - not:
                            anyOf:
                            - required:
                              - literal
                            - required:
                              - environment
                            - required:
                              - header
                        - required:
                          - literal
                        - required:
                          - environment
                        - required:
                          - header
                        properties:
                          environment:
                            description: Environment adds the value of an environment
                              variable to each span.
                            properties:
                              defaultValue:
                                description: Optional.
                                type: string
                              name:
                                description: Name of the environment variable from
                                  which to extract the tag value.
                                minLength: 1
                                type: string
                            required:
                            - name
                            type: object
                          header:
                            description: RequestHeader adds the value of an header
                              from the request to each span.
                            properties:
                              defaultValue:
                                description: Optional.
                                type: string
                              name:
                                description: Name of the header from which to extract
                                  the tag value.
                                minLength: 1
                                type: string
                            required:
                            - name
                            type: object
                          literal:
                            description: Literal adds the same, hard-coded value to
                              each span.
                            properties:
                              value:
                                description: The tag value to use.
                                minLength: 1
                                type: string
                            required:
                            - value
                            type: object
                        type: object
                      description: Optional.
                      type: object
                    disableSpanReporting:
                      description: Controls span reporting.
                      nullable: true
                      type: boolean
                    match:
                      description: Allows tailoring of behavior to specific conditions.
                      properties:
                        mode:
                          description: |-
                            This determines whether or not to apply the tracing configuration based on the direction of traffic relative to the proxied workload.

                            Valid Options: CLIENT_AND_SERVER, CLIENT, SERVER
                          enum:
                          - CLIENT_AND_SERVER
                          - CLIENT
                          - SERVER
                          type: string
                      type: object
                    providers:
                      description: Optional.
                      items:
                        properties:
                          name:
                            description: Required.
                            minLength: 1
                            type: string
                        required:
                        - name
                        type: object
                      type: array
                    randomSamplingPercentage:
                      description: Controls the rate at which traffic will be selected
                        for tracing if no prior sampling decision has been made.
                      format: double
                      maximum: 100
                      minimum: 0
                      nullable: true
                      type: number
                    useRequestIdForTraceSampling:
                      nullable: true
                      type: boolean
                  type: object
                type: array
            type: object
          status:
            type: object
            x-kubernetes-preserve-unknown-fields: true
        type: object
    served: true
    storage: false
    subresources:
      status: {}
  - additionalPrinterColumns:
    - description: 'CreationTimestamp is a timestamp representing the server time
        when this object was created. It is not guaranteed to be set in happens-before
        order across separate operations. Clients may not set this value. It is represented
        in RFC3339 form and is in UTC. Populated by the system. Read-only. Null for
        lists. More info: https://git.k8s.io/community/contributors/devel/api-conventions.md#metadata'
      jsonPath: .metadata.creationTimestamp
      name: Age
      type: date
    name: v1alpha1
    schema:
      openAPIV3Schema:
        properties:
          spec:
            description: 'Telemetry configuration for workloads. See more details
              at: https://istio.io/docs/reference/config/telemetry.html'
            properties:
              accessLogging:
                description: Optional.
                items:
                  properties:
                    disabled:
                      description: Controls logging.
                      nullable: true
                      type: boolean
                    filter:
                      description: Optional.
                      properties:
                        expression:
                          description: CEL expression for selecting when requests/connections
                            should be logged.
                          type: string
                      type: object
                    match:
                      description: Allows tailoring of logging behavior to specific
                        conditions.
                      properties:
                        mode:
                          description: |-
                            This determines whether or not to apply the access logging configuration based on the direction of traffic relative to the proxied workload.

                            Valid Options: CLIENT_AND_SERVER, CLIENT, SERVER
                          enum:
                          - CLIENT_AND_SERVER
                          - CLIENT
                          - SERVER
                          type: string
                      type: object
                    providers:
                      description: Optional.
                      items:
                        properties:
                          name:
                            description: Required.
                            minLength: 1
                            type: string
                        required:
                        - name
                        type: object
                      type: array
                  type: object
                type: array
              metrics:
                description: Optional.
                items:
                  properties:
                    overrides:
                      description: Optional.
                      items:
                        properties:
                          disabled:
                            description: Optional.
                            nullable: true
                            type: boolean
                          match:
                            description: Match allows providing the scope of the override.
                            oneOf:
                            - not:
                                anyOf:
                                - required:
                                  - metric
                                - required:
                                  - customMetric
                            - required:
                              - metric
                            - required:
                              - customMetric
                            properties:
                              customMetric:
                                description: Allows free-form specification of a metric.
                                minLength: 1
                                type: string
                              metric:
                                description: |-
                                  One of the well-known [Istio Standard Metrics](https://istio.io/latest/docs/reference/config/metrics/).

                                  Valid Options: ALL_METRICS, REQUEST_COUNT, REQUEST_DURATION, REQUEST_SIZE, RESPONSE_SIZE, TCP_OPENED_CONNECTIONS, TCP_CLOSED_CONNECTIONS, TCP_SENT_BYTES, TCP_RECEIVED_BYTES, GRPC_REQUEST_MESSAGES, GRPC_RESPONSE_MESSAGES
                                enum:
                                - ALL_METRICS
                                - REQUEST_COUNT
                                - REQUEST_DURATION
                                - REQUEST_SIZE
                                - RESPONSE_SIZE
                                - TCP_OPENED_CONNECTIONS
                                - TCP_CLOSED_CONNECTIONS
                                - TCP_SENT_BYTES
                                - TCP_RECEIVED_BYTES
                                - GRPC_REQUEST_MESSAGES
                                - GRPC_RESPONSE_MESSAGES
                                type: string
                              mode:
                                description: |-
                                  Controls which mode of metrics generation is selected: `CLIENT`, `SERVER`, or `CLIENT_AND_SERVER`.

                                  Valid Options: CLIENT_AND_SERVER, CLIENT, SERVER
                                enum:
                                - CLIENT_AND_SERVER
                                - CLIENT
                                - SERVER
                                type: string
                            type: object
                          tagOverrides:
                            additionalProperties:
                              properties:
                                operation:
                                  description: |-
                                    Operation controls whether or not to update/add a tag, or to remove it.

                                    Valid Options: UPSERT, REMOVE
                                  enum:
                                  - UPSERT
                                  - REMOVE
                                  type: string
                                value:
                                  description: Value is only considered if the operation
                                    is `UPSERT`.
                                  type: string
                              type: object
                              x-kubernetes-validations:
                              - message: value must be set when operation is UPSERT
                                rule: '((has(self.operation) ? self.operation : '''')
                                  == ''UPSERT'') ? self.value != '''' : true'
                              - message: value must not be set when operation is REMOVE
                                rule: '((has(self.operation) ? self.operation : '''')
                                  == ''REMOVE'') ? !has(self.value) : true'
                            description: Optional.
                            type: object
                        type: object
                      type: array
                    providers:
                      description: Optional.
                      items:
                        properties:
                          name:
                            description: Required.
                            minLength: 1
                            type: string
                        required:
                        - name
                        type: object
                      type: array
                    reportingInterval:
                      description: Optional.
                      type: string
                      x-kubernetes-validations:
                      - message: must be a valid duration greater than 1ms
                        rule: duration(self) >= duration('1ms')
                  type: object
                type: array
              selector:
                description: Optional.
                properties:
                  matchLabels:
                    additionalProperties:
                      maxLength: 63
                      type: string
                      x-kubernetes-validations:
                      - message: wildcard not allowed in label value match
                        rule: '!self.contains(''*'')'
                    description: One or more labels that indicate a specific set of
                      pods/VMs on which a policy should be applied.
                    maxProperties: 4096
                    type: object
                    x-kubernetes-validations:
                    - message: wildcard not allowed in label key match
                      rule: self.all(key, !key.contains('*'))
                    - message: key must not be empty
                      rule: self.all(key, key.size() != 0)
                type: object
              targetRef:
                properties:
                  group:
                    description: group is the group of the target resource.
                    maxLength: 253
                    pattern: ^$|^[a-z0-9]([-a-z0-9]*[a-z0-9])?(\.[a-z0-9]([-a-z0-9]*[a-z0-9])?)*$
                    type: string
                  kind:
                    description: kind is kind of the target resource.
                    maxLength: 63
                    minLength: 1
                    pattern: ^[a-zA-Z]([-a-zA-Z0-9]*[a-zA-Z0-9])?$
                    type: string
                  name:
                    description: name is the name of the target resource.
                    maxLength: 253
                    minLength: 1
                    type: string
                  namespace:
                    description: namespace is the namespace of the referent.
                    type: string
                    x-kubernetes-validations:
                    - message: cross namespace referencing is not currently supported
                      rule: self.size() == 0
                required:
                - kind
                - name
                type: object
                x-kubernetes-validations:
                - message: Support kinds are core/Service and gateway.networking.k8s.io/Gateway
                  rule: '[self.group, self.kind] in [[''core'',''Service''], ['''',''Service''],
                    [''gateway.networking.k8s.io'',''Gateway'']]'
              targetRefs:
                description: Optional.
                items:
                  properties:
                    group:
                      description: group is the group of the target resource.
                      maxLength: 253
                      pattern: ^$|^[a-z0-9]([-a-z0-9]*[a-z0-9])?(\.[a-z0-9]([-a-z0-9]*[a-z0-9])?)*$
                      type: string
                    kind:
                      description: kind is kind of the target resource.
                      maxLength: 63
                      minLength: 1
                      pattern: ^[a-zA-Z]([-a-zA-Z0-9]*[a-zA-Z0-9])?$
                      type: string
                    name:
                      description: name is the name of the target resource.
                      maxLength: 253
                      minLength: 1
                      type: string
                    namespace:
                      description: namespace is the namespace of the referent.
                      type: string
                      x-kubernetes-validations:
                      - message: cross namespace referencing is not currently supported
                        rule: self.size() == 0
                  required:
                  - kind
                  - name
                  type: object
                  x-kubernetes-validations:
                  - message: Support kinds are core/Service and gateway.networking.k8s.io/Gateway
                    rule: '[self.group, self.kind] in [[''core'',''Service''], ['''',''Service''],
                      [''gateway.networking.k8s.io'',''Gateway'']]'
                type: array
              tracing:
                description: Optional.
                items:
                  properties:
                    customTags:
                      additionalProperties:
                        oneOf:
                        - not:
                            anyOf:
                            - required:
                              - literal
                            - required:
                              - environment
                            - required:
                              - header
                        - required:
                          - literal
                        - required:
                          - environment
                        - required:
                          - header
                        properties:
                          environment:
                            description: Environment adds the value of an environment
                              variable to each span.
                            properties:
                              defaultValue:
                                description: Optional.
                                type: string
                              name:
                                description: Name of the environment variable from
                                  which to extract the tag value.
                                minLength: 1
                                type: string
                            required:
                            - name
                            type: object
                          header:
                            description: RequestHeader adds the value of an header
                              from the request to each span.
                            properties:
                              defaultValue:
                                description: Optional.
                                type: string
                              name:
                                description: Name of the header from which to extract
                                  the tag value.
                                minLength: 1
                                type: string
                            required:
                            - name
                            type: object
                          literal:
                            description: Literal adds the same, hard-coded value to
                              each span.
                            properties:
                              value:
                                description: The tag value to use.
                                minLength: 1
                                type: string
                            required:
                            - value
                            type: object
                        type: object
                      description: Optional.
                      type: object
                    disableSpanReporting:
                      description: Controls span reporting.
                      nullable: true
                      type: boolean
                    match:
                      description: Allows tailoring of behavior to specific conditions.
                      properties:
                        mode:
                          description: |-
                            This determines whether or not to apply the tracing configuration based on the direction of traffic relative to the proxied workload.

                            Valid Options: CLIENT_AND_SERVER, CLIENT, SERVER
                          enum:
                          - CLIENT_AND_SERVER
                          - CLIENT
                          - SERVER
                          type: string
                      type: object
                    providers:
                      description: Optional.
                      items:
                        properties:
                          name:
                            description: Required.
                            minLength: 1
                            type: string
                        required:
                        - name
                        type: object
                      type: array
                    randomSamplingPercentage:
                      description: Controls the rate at which traffic will be selected
                        for tracing if no prior sampling decision has been made.
                      format: double
                      maximum: 100
                      minimum: 0
                      nullable: true
                      type: number
                    useRequestIdForTraceSampling:
                      nullable: true
                      type: boolean
                  type: object
                type: array
            type: object
          status:
            type: object
            x-kubernetes-preserve-unknown-fields: true
        type: object
    served: true
    storage: true
    subresources:
      status: {}
---
# Source: istio-microk8s/charts/base/templates/zzz_profile.yaml
#  Flatten globals, if defined on a per-chart basis
---
# Source: istio-microk8s/charts/base/templates/default.yaml
apiVersion: admissionregistration.k8s.io/v1
kind: ValidatingWebhookConfiguration
metadata:
  name: istiod-default-validator
  labels:
    app: istiod
    release: release-20240822
    istio: istiod
    istio.io/rev: "default"
webhooks:
  - name: validation.istio.io
    clientConfig:
      service:
        name: istiod
        namespace: istio-system
        path: "/validate"
    rules:
      - operations:
          - CREATE
          - UPDATE
        apiGroups:
          - security.istio.io
          - networking.istio.io
          - telemetry.istio.io
          - extensions.istio.io
        apiVersions:
          - "*"
        resources:
          - "*"
    # Fail open until the validation webhook is ready. The webhook controller
    # will update this to `Fail` and patch in the `caBundle` when the webhook
    # endpoint is ready.
    failurePolicy: Ignore
    sideEffects: None
    admissionReviewVersions: ["v1"]
```

</details>


続いて、istiodをインストールする。

```bash
$ microk8s helm3 template release-$(date '+%Y%m%d') . --set istiod.enabled=true -n istio-system | microk8s kubectl apply -f -

poddisruptionbudget.policy/istiod created
serviceaccount/istiod created
configmap/istio created
configmap/istio-sidecar-injector created
clusterrole.rbac.authorization.k8s.io/istiod-clusterrole-istio-system created
clusterrole.rbac.authorization.k8s.io/istiod-gateway-controller-istio-system created
clusterrole.rbac.authorization.k8s.io/istio-reader-clusterrole-istio-system created
clusterrolebinding.rbac.authorization.k8s.io/istiod-clusterrole-istio-system created
clusterrolebinding.rbac.authorization.k8s.io/istiod-gateway-controller-istio-system created
clusterrolebinding.rbac.authorization.k8s.io/istio-reader-clusterrole-istio-system created
role.rbac.authorization.k8s.io/istiod created
rolebinding.rbac.authorization.k8s.io/istiod created
service/istiod created
deployment.apps/istiod created
horizontalpodautoscaler.autoscaling/istiod created
mutatingwebhookconfiguration.admissionregistration.k8s.io/istio-sidecar-injector created
validatingwebhookconfiguration.admissionregistration.k8s.io/istio-validator-istio-system created

$ microk8s kubectl get all --all-namespaces
NAMESPACE        NAME                                                  READY   STATUS    RESTARTS      AGE
default          pod/keycloak-23-0                                     1/1     Running   0             23d
default          pod/keycloak-23-postgresql-0                          1/1     Running   0             23d
default          pod/postgres-7f8c6b8ddf-9j6tx                         1/1     Running   0             24d
default          pod/sample-kubernetes-dbaccess-app-6f6dc66cb7-glbwj   1/1     Running   0             23d
default          pod/service-mesh-app-service3-6bc878669c-s6kfd        1/1     Running   0             8d
istio-system     pod/istiod-5f7944b777-gxt5k                           1/1     Running   0             66s
kafka            pod/sample-cluster-entity-operator-dbdbbcb78-46ss8    2/2     Running   0             14d
kafka            pod/sample-cluster-kafka-0                            1/1     Running   0             14d
kafka            pod/sample-cluster-zookeeper-0                        1/1     Running   0             14d
kafka            pod/strimzi-cluster-operator-6948497896-4wfdj         1/1     Running   0             21d
kube-system      pod/calico-kube-controllers-796fb75cc-lh9kb           1/1     Running   0             27d
kube-system      pod/calico-node-zxvfd                                 1/1     Running   0             22d
kube-system      pod/coredns-5986966c54-2fm58                          1/1     Running   0             27d
kube-system      pod/hostpath-provisioner-7c8bdf94b8-s4b45             1/1     Running   1 (22d ago)   24d
metallb-system   pod/controller-5484c5f99f-xlcsf                       1/1     Running   0             24d
metallb-system   pod/speaker-2vpvq                                     1/1     Running   0             24d

NAMESPACE        NAME                                              TYPE           CLUSTER-IP       EXTERNAL-IP   PORT(S)                                        AGE
default          service/keycloak-23                               ClusterIP      10.152.183.67    <none>        80/TCP                                         23d
default          service/keycloak-23-headless                      ClusterIP      None             <none>        8080/TCP                                       23d
default          service/keycloak-23-postgresql                    ClusterIP      10.152.183.227   <none>        5432/TCP                                       23d
default          service/keycloak-23-postgresql-hl                 ClusterIP      None             <none>        5432/TCP                                       23d
default          service/kubernetes                                ClusterIP      10.152.183.1     <none>        443/TCP                                        27d
default          service/postgres                                  NodePort       10.152.183.56    <none>        5432:30432/TCP                                 24d
default          service/sample-kubernetes-dbaccess-app            ClusterIP      10.152.183.180   <none>        8080/TCP                                       23d
default          service/sample-kubernetes-dbaccess-app-lb         LoadBalancer   10.152.183.196   192.168.1.0   8080:31570/TCP                                 23d
default          service/service-mesh-app-service3                 ClusterIP      10.152.183.51    <none>        8080/TCP                                       8d
istio-system     service/istiod                                    ClusterIP      10.152.183.58    <none>        15010/TCP,15012/TCP,443/TCP,15014/TCP          66s
kafka            service/sample-cluster-kafka-0                    LoadBalancer   10.152.183.95    192.168.1.2   9094:31141/TCP                                 14d
kafka            service/sample-cluster-kafka-bootstrap            ClusterIP      10.152.183.165   <none>        9091/TCP,9092/TCP,9093/TCP                     14d
kafka            service/sample-cluster-kafka-brokers              ClusterIP      None             <none>        9090/TCP,9091/TCP,8443/TCP,9092/TCP,9093/TCP   14d
kafka            service/sample-cluster-kafka-external-bootstrap   LoadBalancer   10.152.183.70    192.168.1.1   9094:30113/TCP                                 14d
kafka            service/sample-cluster-zookeeper-client           ClusterIP      10.152.183.45    <none>        2181/TCP                                       14d
kafka            service/sample-cluster-zookeeper-nodes            ClusterIP      None             <none>        2181/TCP,2888/TCP,3888/TCP                     14d
kube-system      service/kube-dns                                  ClusterIP      10.152.183.10    <none>        53/UDP,53/TCP,9153/TCP                         27d
metallb-system   service/webhook-service                           ClusterIP      10.152.183.57    <none>        443/TCP                                        24d

NAMESPACE        NAME                         DESIRED   CURRENT   READY   UP-TO-DATE   AVAILABLE   NODE SELECTOR            AGE
kube-system      daemonset.apps/calico-node   1         1         1       1            1           kubernetes.io/os=linux   27d
metallb-system   daemonset.apps/speaker       1         1         1       1            1           kubernetes.io/os=linux   24d

NAMESPACE        NAME                                             READY   UP-TO-DATE   AVAILABLE   AGE
default          deployment.apps/postgres                         1/1     1            1           24d
default          deployment.apps/sample-kubernetes-dbaccess-app   1/1     1            1           23d
default          deployment.apps/service-mesh-app-service3        1/1     1            1           8d
istio-system     deployment.apps/istiod                           1/1     1            1           66s
kafka            deployment.apps/sample-cluster-entity-operator   1/1     1            1           14d
kafka            deployment.apps/strimzi-cluster-operator         1/1     1            1           21d
kube-system      deployment.apps/calico-kube-controllers          1/1     1            1           27d
kube-system      deployment.apps/coredns                          1/1     1            1           27d
kube-system      deployment.apps/hostpath-provisioner             1/1     1            1           24d
metallb-system   deployment.apps/controller                       1/1     1            1           24d

NAMESPACE        NAME                                                        DESIRED   CURRENT   READY   AGE
default          replicaset.apps/postgres-7f8c6b8ddf                         1         1         1       24d
default          replicaset.apps/sample-kubernetes-dbaccess-app-6f6dc66cb7   1         1         1       23d
default          replicaset.apps/service-mesh-app-service3-6bc878669c        1         1         1       8d
istio-system     replicaset.apps/istiod-5f7944b777                           1         1         1       66s
kafka            replicaset.apps/sample-cluster-entity-operator-dbdbbcb78    1         1         1       14d
kafka            replicaset.apps/strimzi-cluster-operator-6948497896         1         1         1       21d
kube-system      replicaset.apps/calico-kube-controllers-796fb75cc           1         1         1       27d
kube-system      replicaset.apps/coredns-5986966c54                          1         1         1       27d
kube-system      replicaset.apps/hostpath-provisioner-7c8bdf94b8             1         1         1       24d
metallb-system   replicaset.apps/controller-5484c5f99f                       1         1         1       24d

NAMESPACE   NAME                                      READY   AGE
default     statefulset.apps/keycloak-23              1/1     23d
default     statefulset.apps/keycloak-23-postgresql   1/1     23d

NAMESPACE      NAME                                         REFERENCE           TARGETS              MINPODS   MAXPODS   REPLICAS   AGE
istio-system   horizontalpodautoscaler.autoscaling/istiod   Deployment/istiod   cpu: <unknown>/80%   1         5         1          66s

```

**NOTE:** 上記のコマンド実行時には以下のyamlテンプレート(v1.23.0)が適用される。


```yaml
---
# Source: istio-microk8s/charts/istiod/templates/poddisruptionbudget.yaml
apiVersion: policy/v1
kind: PodDisruptionBudget
metadata:
  name: istiod
  namespace: istio-system
  labels:
    app: istiod
    istio.io/rev: "default"
    install.operator.istio.io/owning-resource: unknown
    operator.istio.io/component: "Pilot"
    release: release-20240821
    istio: pilot
spec:
  minAvailable: 1
  selector:
    matchLabels:
      app: istiod
      istio: pilot
---
# Source: istio-microk8s/charts/istiod/templates/serviceaccount.yaml
apiVersion: v1
kind: ServiceAccount
metadata:
  name: istiod
  namespace: istio-system
  labels:
    app: istiod
    release: release-20240821
---
# Source: istio-microk8s/charts/istiod/templates/configmap.yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: istio
  namespace: istio-system
  labels:
    istio.io/rev: "default"
    install.operator.istio.io/owning-resource: unknown
    operator.istio.io/component: "Pilot"
    release: release-20240821
data:

  # Configuration file for the mesh networks to be used by the Split Horizon EDS.
  meshNetworks: |-
    networks: {}

  mesh: |-
    defaultConfig:
      discoveryAddress: istiod.istio-system.svc:15012
    defaultProviders:
      metrics:
      - prometheus
    enablePrometheusMerge: true
    rootNamespace: istio-system
    trustDomain: cluster.local
---
# Source: istio-microk8s/charts/istiod/templates/istiod-injector-configmap.yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: istio-sidecar-injector
  namespace: istio-system
  labels:
    istio.io/rev: "default"
    install.operator.istio.io/owning-resource: unknown
    operator.istio.io/component: "Pilot"
    release: release-20240821
data:

  values: |-
    {
      "gateways": {
        "seccompProfile": {},
        "securityContext": {}
      },
      "global": {
        "autoscalingv2API": true,
        "caAddress": "",
        "caName": "",
        "certSigners": [],
        "configCluster": false,
        "configValidation": true,
        "defaultPodDisruptionBudget": {
          "enabled": true
        },
        "defaultResources": {
          "requests": {
            "cpu": "10m"
          }
        },
        "externalIstiod": false,
        "hub": "docker.io/istio",
        "imagePullPolicy": "",
        "imagePullSecrets": [],
        "istioNamespace": "istio-system",
        "istiod": {
          "enableAnalysis": false
        },
        "logAsJson": false,
        "logging": {
          "level": "default:info"
        },
        "meshID": "",
        "meshNetworks": {},
        "mountMtlsCerts": false,
        "multiCluster": {
          "clusterName": "",
          "enabled": false
        },
        "network": "",
        "omitSidecarInjectorConfigMap": false,
        "operatorManageWebhooks": false,
        "pilotCertProvider": "istiod",
        "priorityClassName": "",
        "proxy": {
          "autoInject": "enabled",
          "clusterDomain": "cluster.local",
          "componentLogLevel": "misc:error",
          "enableCoreDump": false,
          "excludeIPRanges": "",
          "excludeInboundPorts": "",
          "excludeOutboundPorts": "",
          "image": "proxyv2",
          "includeIPRanges": "*",
          "includeInboundPorts": "*",
          "includeOutboundPorts": "",
          "logLevel": "warning",
          "outlierLogPath": "",
          "privileged": false,
          "readinessFailureThreshold": 4,
          "readinessInitialDelaySeconds": 0,
          "readinessPeriodSeconds": 15,
          "resources": {
            "limits": {
              "cpu": "2000m",
              "memory": "1024Mi"
            },
            "requests": {
              "cpu": "100m",
              "memory": "128Mi"
            }
          },
          "startupProbe": {
            "enabled": true,
            "failureThreshold": 600
          },
          "statusPort": 15020,
          "tracer": "none"
        },
        "proxy_init": {
          "image": "proxyv2"
        },
        "remotePilotAddress": "",
        "sds": {
          "token": {
            "aud": "istio-ca"
          }
        },
        "sts": {
          "servicePort": 0
        },
        "tag": "1.23.0",
        "variant": ""
      },
      "istio_cni": {
        "chained": true,
        "provider": "default"
      },
      "pilot": {
        "cni": {
          "enabled": false,
          "provider": "default"
        }
      },
      "revision": "",
      "sidecarInjectorWebhook": {
        "alwaysInjectSelector": [],
        "defaultTemplates": [],
        "enableNamespacesByDefault": false,
        "injectedAnnotations": {},
        "neverInjectSelector": [],
        "reinvocationPolicy": "Never",
        "rewriteAppHTTPProbe": true,
        "templates": {}
      }
    }

  # To disable injection: use omitSidecarInjectorConfigMap, which disables the webhook patching
  # and istiod webhook functionality.
  #
  # New fields should not use Values - it is a 'primary' config object, users should be able
  # to fine tune it or use it with kube-inject.
  config: |-
    # defaultTemplates defines the default template to use for pods that do not explicitly specify a template
    defaultTemplates: [sidecar]
    policy: enabled
    alwaysInjectSelector:
      []
    neverInjectSelector:
      []
    injectedAnnotations:
    template: "{{ Template_Version_And_Istio_Version_Mismatched_Check_Installation }}"
    templates:
      sidecar: |
        {{- define "resources"  }}
          {{- if or (isset .ObjectMeta.Annotations `sidecar.istio.io/proxyCPU`) (isset .ObjectMeta.Annotations `sidecar.istio.io/proxyMemory`) (isset .ObjectMeta.Annotations `sidecar.istio.io/proxyCPULimit`) (isset .ObjectMeta.Annotations `sidecar.istio.io/proxyMemoryLimit`) }}
            {{- if or (isset .ObjectMeta.Annotations `sidecar.istio.io/proxyCPU`) (isset .ObjectMeta.Annotations `sidecar.istio.io/proxyMemory`) }}
#
# TL;DR // omit
#
        spec:
          ports:
          {{- range $key, $val := .Ports }}
          - name: {{ $val.Name | quote }}
            port: {{ $val.Port }}
            protocol: TCP
            appProtocol: {{ $val.AppProtocol }}
          {{- end }}
          selector:
            "{{.GatewayNameLabel}}": {{.Name}}
          {{- if and (.Spec.Addresses) (eq .ServiceType "LoadBalancer") }}
          loadBalancerIP: {{ (index .Spec.Addresses 0).Value | quote}}
          {{- end }}
          type: {{ .ServiceType | quote }}
        ---
---
# Source: istio-microk8s/charts/istiod/templates/clusterrole.yaml
apiVersion: rbac.authorization.k8s.io/v1
kind: ClusterRole
metadata:
  name: istiod-clusterrole-istio-system
  labels:
    app: istiod
    release: release-20240821
rules:
  # sidecar injection controller
  - apiGroups: ["admissionregistration.k8s.io"]
    resources: ["mutatingwebhookconfigurations"]
    verbs: ["get", "list", "watch", "update", "patch"]

  # configuration validation webhook controller
  - apiGroups: ["admissionregistration.k8s.io"]
    resources: ["validatingwebhookconfigurations"]
    verbs: ["get", "list", "watch", "update"]

  # istio configuration
  # removing CRD permissions can break older versions of Istio running alongside this control plane (https://github.com/istio/istio/issues/29382)
  # please proceed with caution
  - apiGroups: ["config.istio.io", "security.istio.io", "networking.istio.io", "authentication.istio.io", "rbac.istio.io", "telemetry.istio.io", "extensions.istio.io"]
    verbs: ["get", "watch", "list"]
    resources: ["*"]
  - apiGroups: ["networking.istio.io"]
    verbs: [ "get", "watch", "list", "update", "patch", "create", "delete" ]
    resources: [ "workloadentries" ]
  - apiGroups: ["networking.istio.io"]
    verbs: [ "get", "watch", "list", "update", "patch", "create", "delete" ]
    resources: [ "workloadentries/status" ]

  - apiGroups: ["networking.istio.io"]
    verbs: [ "get", "watch", "list", "update", "patch" ]
    resources: [ "serviceentries/status" ]

  # auto-detect installed CRD definitions
  - apiGroups: ["apiextensions.k8s.io"]
    resources: ["customresourcedefinitions"]
    verbs: ["get", "list", "watch"]

  # discovery and routing
  - apiGroups: [""]
    resources: ["pods", "nodes", "services", "namespaces", "endpoints"]
    verbs: ["get", "list", "watch"]
  - apiGroups: ["discovery.k8s.io"]
    resources: ["endpointslices"]
    verbs: ["get", "list", "watch"]

  # ingress controller
  - apiGroups: ["networking.k8s.io"]
    resources: ["ingresses", "ingressclasses"]
    verbs: ["get", "list", "watch"]
  - apiGroups: ["networking.k8s.io"]
    resources: ["ingresses/status"]
    verbs: ["*"]

  # required for CA's namespace controller
  - apiGroups: [""]
    resources: ["configmaps"]
    verbs: ["create", "get", "list", "watch", "update"]

  # Istiod and bootstrap.

  # Used by Istiod to verify the JWT tokens
  - apiGroups: ["authentication.k8s.io"]
    resources: ["tokenreviews"]
    verbs: ["create"]

  # Used by Istiod to verify gateway SDS
  - apiGroups: ["authorization.k8s.io"]
    resources: ["subjectaccessreviews"]
    verbs: ["create"]

  # Use for Kubernetes Service APIs
  - apiGroups: ["networking.x-k8s.io", "gateway.networking.k8s.io"]
    resources: ["*"]
    verbs: ["get", "watch", "list"]
  - apiGroups: ["networking.x-k8s.io", "gateway.networking.k8s.io"]
    resources: ["*"] # TODO: should be on just */status but wildcard is not supported
    verbs: ["update", "patch"]
  - apiGroups: ["gateway.networking.k8s.io"]
    resources: ["gatewayclasses"]
    verbs: ["create", "update", "patch", "delete"]

  # Needed for multicluster secret reading, possibly ingress certs in the future
  - apiGroups: [""]
    resources: ["secrets"]
    verbs: ["get", "watch", "list"]

  # Used for MCS serviceexport management
  - apiGroups: ["multicluster.x-k8s.io"]
    resources: ["serviceexports"]
    verbs: [ "get", "watch", "list", "create", "delete"]

  # Used for MCS serviceimport management
  - apiGroups: ["multicluster.x-k8s.io"]
    resources: ["serviceimports"]
    verbs: ["get", "watch", "list"]
---
# Source: istio-microk8s/charts/istiod/templates/clusterrole.yaml
apiVersion: rbac.authorization.k8s.io/v1
kind: ClusterRole
metadata:
  name: istiod-gateway-controller-istio-system
  labels:
    app: istiod
    release: release-20240821
rules:
  - apiGroups: ["apps"]
    verbs: [ "get", "watch", "list", "update", "patch", "create", "delete" ]
    resources: [ "deployments" ]
  - apiGroups: [""]
    verbs: [ "get", "watch", "list", "update", "patch", "create", "delete" ]
    resources: [ "services" ]
  - apiGroups: [""]
    verbs: [ "get", "watch", "list", "update", "patch", "create", "delete" ]
    resources: [ "serviceaccounts"]
---
# Source: istio-microk8s/charts/istiod/templates/reader-clusterrole.yaml
apiVersion: rbac.authorization.k8s.io/v1
kind: ClusterRole
metadata:
  name: istio-reader-clusterrole-istio-system
  labels:
    app: istio-reader
    release: release-20240821
rules:
  - apiGroups:
      - "config.istio.io"
      - "security.istio.io"
      - "networking.istio.io"
      - "authentication.istio.io"
      - "rbac.istio.io"
      - "telemetry.istio.io"
      - "extensions.istio.io"
    resources: ["*"]
    verbs: ["get", "list", "watch"]
  - apiGroups: [""]
    resources: ["endpoints", "pods", "services", "nodes", "replicationcontrollers", "namespaces", "secrets"]
    verbs: ["get", "list", "watch"]
  - apiGroups: ["networking.istio.io"]
    verbs: [ "get", "watch", "list" ]
    resources: [ "workloadentries" ]
  - apiGroups: ["networking.x-k8s.io", "gateway.networking.k8s.io"]
    resources: ["gateways"]
    verbs: ["get", "watch", "list"]
  - apiGroups: ["apiextensions.k8s.io"]
    resources: ["customresourcedefinitions"]
    verbs: ["get", "list", "watch"]
  - apiGroups: ["discovery.k8s.io"]
    resources: ["endpointslices"]
    verbs: ["get", "list", "watch"]
  - apiGroups: ["multicluster.x-k8s.io"]
    resources: ["serviceexports"]
    verbs: ["get", "list", "watch", "create", "delete"]
  - apiGroups: ["multicluster.x-k8s.io"]
    resources: ["serviceimports"]
    verbs: ["get", "list", "watch"]
  - apiGroups: ["apps"]
    resources: ["replicasets"]
    verbs: ["get", "list", "watch"]
  - apiGroups: ["authentication.k8s.io"]
    resources: ["tokenreviews"]
    verbs: ["create"]
  - apiGroups: ["authorization.k8s.io"]
    resources: ["subjectaccessreviews"]
    verbs: ["create"]
---
# Source: istio-microk8s/charts/istiod/templates/clusterrolebinding.yaml
apiVersion: rbac.authorization.k8s.io/v1
kind: ClusterRoleBinding
metadata:
  name: istiod-clusterrole-istio-system
  labels:
    app: istiod
    release: release-20240821
roleRef:
  apiGroup: rbac.authorization.k8s.io
  kind: ClusterRole
  name: istiod-clusterrole-istio-system
subjects:
  - kind: ServiceAccount
    name: istiod
    namespace: istio-system
---
# Source: istio-microk8s/charts/istiod/templates/clusterrolebinding.yaml
apiVersion: rbac.authorization.k8s.io/v1
kind: ClusterRoleBinding
metadata:
  name: istiod-gateway-controller-istio-system
  labels:
    app: istiod
    release: release-20240821
roleRef:
  apiGroup: rbac.authorization.k8s.io
  kind: ClusterRole
  name: istiod-gateway-controller-istio-system
subjects:
- kind: ServiceAccount
  name: istiod
  namespace: istio-system
---
# Source: istio-microk8s/charts/istiod/templates/reader-clusterrolebinding.yaml
apiVersion: rbac.authorization.k8s.io/v1
kind: ClusterRoleBinding
metadata:
  name: istio-reader-clusterrole-istio-system
  labels:
    app: istio-reader
    release: release-20240821
roleRef:
  apiGroup: rbac.authorization.k8s.io
  kind: ClusterRole
  name: istio-reader-clusterrole-istio-system
subjects:
  - kind: ServiceAccount
    name: istio-reader-service-account
    namespace: istio-system
---
# Source: istio-microk8s/charts/istiod/templates/role.yaml
apiVersion: rbac.authorization.k8s.io/v1
kind: Role
metadata:
  name: istiod
  namespace: istio-system
  labels:
    app: istiod
    release: release-20240821
rules:
# permissions to verify the webhook is ready and rejecting
# invalid config. We use --server-dry-run so no config is persisted.
- apiGroups: ["networking.istio.io"]
  verbs: ["create"]
  resources: ["gateways"]

# For storing CA secret
- apiGroups: [""]
  resources: ["secrets"]
  # TODO lock this down to istio-ca-cert if not using the DNS cert mesh config
  verbs: ["create", "get", "watch", "list", "update", "delete"]

# For status controller, so it can delete the distribution report configmap
- apiGroups: [""]
  resources: ["configmaps"]
  verbs: ["delete"]

# For gateway deployment controller
- apiGroups: ["coordination.k8s.io"]
  resources: ["leases"]
  verbs: ["get", "update", "patch", "create"]
---
# Source: istio-microk8s/charts/istiod/templates/rolebinding.yaml
apiVersion: rbac.authorization.k8s.io/v1
kind: RoleBinding
metadata:
  name: istiod
  namespace: istio-system
  labels:
    app: istiod
    release: release-20240821
roleRef:
  apiGroup: rbac.authorization.k8s.io
  kind: Role
  name: istiod
subjects:
  - kind: ServiceAccount
    name: istiod
    namespace: istio-system
---
# Source: istio-microk8s/charts/istiod/templates/service.yaml
apiVersion: v1
kind: Service
metadata:
  name: istiod
  namespace: istio-system
  labels:
    istio.io/rev: "default"
    install.operator.istio.io/owning-resource: unknown
    operator.istio.io/component: "Pilot"
    app: istiod
    istio: pilot
    release: release-20240821
spec:
  ports:
    - port: 15010
      name: grpc-xds # plaintext
      protocol: TCP
    - port: 15012
      name: https-dns # mTLS with k8s-signed cert
      protocol: TCP
    - port: 443
      name: https-webhook # validation and injection
      targetPort: 15017
      protocol: TCP
    - port: 15014
      name: http-monitoring # prometheus stats
      protocol: TCP
  selector:
    app: istiod
    # Label used by the 'default' service. For versioned deployments we match with app and version.
    # This avoids default deployment picking the canary
    istio: pilot
---
# Source: istio-microk8s/charts/istiod/templates/deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: istiod
  namespace: istio-system
  labels:
    app: istiod
    istio.io/rev: "default"
    install.operator.istio.io/owning-resource: unknown
    operator.istio.io/component: "Pilot"
    istio: pilot
    release: release-20240821
spec:
  strategy:
    rollingUpdate:
      maxSurge: 100%
      maxUnavailable: 25%
  selector:
    matchLabels:
      istio: pilot
  template:
    metadata:
      labels:
        app: istiod
        istio.io/rev: "default"
        install.operator.istio.io/owning-resource: unknown
        sidecar.istio.io/inject: "false"
        operator.istio.io/component: "Pilot"
        istio: pilot
        istio.io/dataplane-mode: none
      annotations:
        prometheus.io/port: "15014"
        prometheus.io/scrape: "true"
        sidecar.istio.io/inject: "false"
    spec:
      tolerations:
        - key: cni.istio.io/not-ready
          operator: "Exists"
      serviceAccountName: istiod
      containers:
        - name: discovery
          image: "docker.io/istio/pilot:1.23.0"
          args:
          - "discovery"
          - --monitoringAddr=:15014
          - --log_output_level=default:info
          - --domain
          - cluster.local
          - --keepaliveMaxServerConnectionAge
          - "30m"
          ports:
          - containerPort: 8080
            protocol: TCP
          - containerPort: 15010
            protocol: TCP
          - containerPort: 15017
            protocol: TCP
          readinessProbe:
            httpGet:
              path: /ready
              port: 8080
            initialDelaySeconds: 1
            periodSeconds: 3
            timeoutSeconds: 5
          env:
          - name: REVISION
            value: "default"
          - name: PILOT_CERT_PROVIDER
            value: istiod
          - name: POD_NAME
            valueFrom:
              fieldRef:
                apiVersion: v1
                fieldPath: metadata.name
          - name: POD_NAMESPACE
            valueFrom:
              fieldRef:
                apiVersion: v1
                fieldPath: metadata.namespace
          - name: SERVICE_ACCOUNT
            valueFrom:
              fieldRef:
                apiVersion: v1
                fieldPath: spec.serviceAccountName
          - name: KUBECONFIG
            value: /var/run/secrets/remote/config
          # If you explicitly told us where ztunnel lives, use that.
          # Otherwise, assume it lives in our namespace
          # Also, check for an explicit ENV override (legacy approach) and prefer that
          # if present

          - name: CA_TRUSTED_NODE_ACCOUNTS
            value: "istio-system/ztunnel"
          - name: PILOT_TRACE_SAMPLING
            value: "1"
# If externalIstiod is set via Values.Global, then enable the pilot env variable. However, if it's set via Values.pilot.env, then
# don't set it here to avoid duplication.
          - name: PILOT_ENABLE_ANALYSIS
            value: "false"
          - name: CLUSTER_ID
            value: "Kubernetes"
          - name: GOMEMLIMIT
            valueFrom:
              resourceFieldRef:
                resource: limits.memory
          - name: GOMAXPROCS
            valueFrom:
              resourceFieldRef:
                resource: limits.cpu
          - name: PLATFORM
            value: ""
          resources:
            requests:
              cpu: 500m
              memory: 2048Mi
          securityContext:
            allowPrivilegeEscalation: false
            readOnlyRootFilesystem: true
            runAsNonRoot: true
            capabilities:
              drop:
              - ALL
          volumeMounts:
          - name: istio-token
            mountPath: /var/run/secrets/tokens
            readOnly: true
          - name: local-certs
            mountPath: /var/run/secrets/istio-dns
          - name: cacerts
            mountPath: /etc/cacerts
            readOnly: true
          - name: istio-kubeconfig
            mountPath: /var/run/secrets/remote
            readOnly: true
          - name: istio-csr-dns-cert
            mountPath: /var/run/secrets/istiod/tls
            readOnly: true
          - name: istio-csr-ca-configmap
            mountPath: /var/run/secrets/istiod/ca
            readOnly: true
      volumes:
      # Technically not needed on this pod - but it helps debugging/testing SDS
      # Should be removed after everything works.
      - emptyDir:
          medium: Memory
        name: local-certs
      - name: istio-token
        projected:
          sources:
            - serviceAccountToken:
                audience: istio-ca
                expirationSeconds: 43200
                path: istio-token
      # Optional: user-generated root
      - name: cacerts
        secret:
          secretName: cacerts
          optional: true
      - name: istio-kubeconfig
        secret:
          secretName: istio-kubeconfig
          optional: true
      # Optional: istio-csr dns pilot certs
      - name: istio-csr-dns-cert
        secret:
          secretName: istiod-tls
          optional: true
      - name: istio-csr-ca-configmap
        configMap:
          name: istio-ca-root-cert
          defaultMode: 420
          optional: true
---
# Source: istio-microk8s/charts/istiod/templates/autoscale.yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: istiod
  namespace: istio-system
  labels:
    app: istiod
    release: release-20240821
    istio.io/rev: "default"
    install.operator.istio.io/owning-resource: unknown
    operator.istio.io/component: "Pilot"
spec:
  maxReplicas: 5
  minReplicas: 1
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: istiod
  metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: 80
---
# Source: istio-microk8s/charts/istiod/templates/revision-tags.yaml
# Adapted from istio-discovery/templates/mutatingwebhook.yaml
# Removed paths for legacy and default selectors since a revision tag
# is inherently created from a specific revision
---
# Source: istio-microk8s/charts/istiod/templates/zzz_profile.yaml
#  Flatten globals, if defined on a per-chart basis
---
# Source: istio-microk8s/charts/istiod/templates/mutatingwebhook.yaml
apiVersion: admissionregistration.k8s.io/v1
kind: MutatingWebhookConfiguration
metadata:
  name: istio-sidecar-injector
  labels:
    istio.io/rev: "default"
    install.operator.istio.io/owning-resource: unknown
    operator.istio.io/component: "Pilot"
    app: sidecar-injector
    release: release-20240821
webhooks:
- name: rev.namespace.sidecar-injector.istio.io
  clientConfig:
    service:
      name: istiod
      namespace: istio-system
      path: "/inject"
      port: 443
  sideEffects: None
  rules:
  - operations: [ "CREATE" ]
    apiGroups: [""]
    apiVersions: ["v1"]
    resources: ["pods"]
  failurePolicy: Fail
  reinvocationPolicy: "Never"
  admissionReviewVersions: ["v1"]
  namespaceSelector:
    matchExpressions:
    - key: istio.io/rev
      operator: In
      values:
      - "default"
    - key: istio-injection
      operator: DoesNotExist
  objectSelector:
    matchExpressions:
    - key: sidecar.istio.io/inject
      operator: NotIn
      values:
      - "false"
- name: rev.object.sidecar-injector.istio.io
  clientConfig:
    service:
      name: istiod
      namespace: istio-system
      path: "/inject"
      port: 443
  sideEffects: None
  rules:
  - operations: [ "CREATE" ]
    apiGroups: [""]
    apiVersions: ["v1"]
    resources: ["pods"]
  failurePolicy: Fail
  reinvocationPolicy: "Never"
  admissionReviewVersions: ["v1"]
  namespaceSelector:
    matchExpressions:
    - key: istio.io/rev
      operator: DoesNotExist
    - key: istio-injection
      operator: DoesNotExist
  objectSelector:
    matchExpressions:
    - key: sidecar.istio.io/inject
      operator: NotIn
      values:
      - "false"
    - key: istio.io/rev
      operator: In
      values:
      - "default"
- name: namespace.sidecar-injector.istio.io
  clientConfig:
    service:
      name: istiod
      namespace: istio-system
      path: "/inject"
      port: 443
  sideEffects: None
  rules:
  - operations: [ "CREATE" ]
    apiGroups: [""]
    apiVersions: ["v1"]
    resources: ["pods"]
  failurePolicy: Fail
  reinvocationPolicy: "Never"
  admissionReviewVersions: ["v1"]
  namespaceSelector:
    matchExpressions:
    - key: istio-injection
      operator: In
      values:
      - enabled
  objectSelector:
    matchExpressions:
    - key: sidecar.istio.io/inject
      operator: NotIn
      values:
      - "false"
- name: object.sidecar-injector.istio.io
  clientConfig:
    service:
      name: istiod
      namespace: istio-system
      path: "/inject"
      port: 443
  sideEffects: None
  rules:
  - operations: [ "CREATE" ]
    apiGroups: [""]
    apiVersions: ["v1"]
    resources: ["pods"]
  failurePolicy: Fail
  reinvocationPolicy: "Never"
  admissionReviewVersions: ["v1"]
  namespaceSelector:
    matchExpressions:
    - key: istio-injection
      operator: DoesNotExist
    - key: istio.io/rev
      operator: DoesNotExist
  objectSelector:
    matchExpressions:
    - key: sidecar.istio.io/inject
      operator: In
      values:
      - "true"
    - key: istio.io/rev
      operator: DoesNotExist
---
# Source: istio-microk8s/charts/istiod/templates/validatingwebhookconfiguration.yaml
apiVersion: admissionregistration.k8s.io/v1
kind: ValidatingWebhookConfiguration
metadata:
  name: istio-validator-istio-system
  labels:
    app: istiod
    release: release-20240821
    istio: istiod
    istio.io/rev: "default"
webhooks:
  # Webhook handling per-revision validation. Mostly here so we can determine whether webhooks
  # are rejecting invalid configs on a per-revision basis.
  - name: rev.validation.istio.io
    clientConfig:
      # Should change from base but cannot for API compat
      service:
        name: istiod
        namespace: istio-system
        path: "/validate"
    rules:
      - operations:
          - CREATE
          - UPDATE
        apiGroups:
          - security.istio.io
          - networking.istio.io
          - telemetry.istio.io
          - extensions.istio.io
        apiVersions:
          - "*"
        resources:
          - "*"
    # Fail open until the validation webhook is ready. The webhook controller
    # will update this to `Fail` and patch in the `caBundle` when the webhook
    # endpoint is ready.
    failurePolicy: Ignore
    sideEffects: None
    admissionReviewVersions: ["v1"]
    objectSelector:
      matchExpressions:
        - key: istio.io/rev
          operator: In
          values:
          - "default"
```

続いて、Istio Ingress gatewayを構築する。

```bash
$ microk8s helm3 template release-$(date '+%Y%m%d') . -n istio-network --set istio-ingressgateway-enabled=true | microk8s  kubectl apply -f -

serviceaccount/istio-ingressgateway created
role.rbac.authorization.k8s.io/istio-ingressgateway created
rolebinding.rbac.authorization.k8s.io/istio-ingressgateway created
service/istio-ingressgateway created
deployment.apps/istio-ingressgateway created
horizontalpodautoscaler.autoscaling/istio-ingressgateway created

$ microk8s kubectl get all --all-namespaces
NAMESPACE        NAME                                                  READY   STATUS    RESTARTS      AGE
default          pod/keycloak-23-0                                     1/1     Running   0             23d
default          pod/keycloak-23-postgresql-0                          1/1     Running   0             23d
default          pod/postgres-7f8c6b8ddf-9j6tx                         1/1     Running   0             24d
default          pod/sample-kubernetes-dbaccess-app-6f6dc66cb7-glbwj   1/1     Running   0             24d
default          pod/service-mesh-app-service3-6bc878669c-s6kfd        1/1     Running   0             8d
istio-network    pod/istio-ingressgateway-78c65fbf55-9tck8             1/1     Running   0             7m10s
istio-system     pod/istiod-5f7944b777-gxt5k                           1/1     Running   0             5h58m
kafka            pod/sample-cluster-entity-operator-dbdbbcb78-46ss8    2/2     Running   0             15d
kafka            pod/sample-cluster-kafka-0                            1/1     Running   0             15d
kafka            pod/sample-cluster-zookeeper-0                        1/1     Running   0             15d
kafka            pod/strimzi-cluster-operator-6948497896-4wfdj         1/1     Running   0             22d
kube-system      pod/calico-kube-controllers-796fb75cc-lh9kb           1/1     Running   0             27d
kube-system      pod/calico-node-zxvfd                                 1/1     Running   0             22d
kube-system      pod/coredns-5986966c54-2fm58                          1/1     Running   0             27d
kube-system      pod/hostpath-provisioner-7c8bdf94b8-s4b45             1/1     Running   1 (22d ago)   24d
metallb-system   pod/controller-5484c5f99f-xlcsf                       1/1     Running   0             24d
metallb-system   pod/speaker-2vpvq                                     1/1     Running   0             24d

NAMESPACE        NAME                                              TYPE           CLUSTER-IP       EXTERNAL-IP   PORT(S)                                        AGE
default          service/keycloak-23                               ClusterIP      10.152.183.67    <none>        80/TCP                                         23d
default          service/keycloak-23-headless                      ClusterIP      None             <none>        8080/TCP                                       23d
default          service/keycloak-23-postgresql                    ClusterIP      10.152.183.227   <none>        5432/TCP                                       23d
default          service/keycloak-23-postgresql-hl                 ClusterIP      None             <none>        5432/TCP                                       23d
default          service/kubernetes                                ClusterIP      10.152.183.1     <none>        443/TCP                                        27d
default          service/postgres                                  NodePort       10.152.183.56    <none>        5432:30432/TCP                                 24d
default          service/sample-kubernetes-dbaccess-app            ClusterIP      10.152.183.180   <none>        8080/TCP                                       24d
default          service/sample-kubernetes-dbaccess-app-lb         LoadBalancer   10.152.183.196   192.168.1.0   8080:31570/TCP                                 23d
default          service/service-mesh-app-service3                 ClusterIP      10.152.183.51    <none>        8080/TCP                                       8d
istio-network    service/istio-ingressgateway                      LoadBalancer   10.152.183.99    192.168.1.3   15021:32164/TCP,80:30821/TCP,443:30554/TCP     7m10s
istio-system     service/istiod                                    ClusterIP      10.152.183.58    <none>        15010/TCP,15012/TCP,443/TCP,15014/TCP          5h58m
kafka            service/sample-cluster-kafka-0                    LoadBalancer   10.152.183.95    192.168.1.2   9094:31141/TCP                                 15d
kafka            service/sample-cluster-kafka-bootstrap            ClusterIP      10.152.183.165   <none>        9091/TCP,9092/TCP,9093/TCP                     15d
kafka            service/sample-cluster-kafka-brokers              ClusterIP      None             <none>        9090/TCP,9091/TCP,8443/TCP,9092/TCP,9093/TCP   15d
kafka            service/sample-cluster-kafka-external-bootstrap   LoadBalancer   10.152.183.70    192.168.1.1   9094:30113/TCP                                 15d
kafka            service/sample-cluster-zookeeper-client           ClusterIP      10.152.183.45    <none>        2181/TCP                                       15d
kafka            service/sample-cluster-zookeeper-nodes            ClusterIP      None             <none>        2181/TCP,2888/TCP,3888/TCP                     15d
kube-system      service/kube-dns                                  ClusterIP      10.152.183.10    <none>        53/UDP,53/TCP,9153/TCP                         27d
metallb-system   service/webhook-service                           ClusterIP      10.152.183.57    <none>        443/TCP                                        24d

NAMESPACE        NAME                         DESIRED   CURRENT   READY   UP-TO-DATE   AVAILABLE   NODE SELECTOR            AGE
kube-system      daemonset.apps/calico-node   1         1         1       1            1           kubernetes.io/os=linux   27d
metallb-system   daemonset.apps/speaker       1         1         1       1            1           kubernetes.io/os=linux   24d

NAMESPACE        NAME                                             READY   UP-TO-DATE   AVAILABLE   AGE
default          deployment.apps/postgres                         1/1     1            1           24d
default          deployment.apps/sample-kubernetes-dbaccess-app   1/1     1            1           24d
default          deployment.apps/service-mesh-app-service3        1/1     1            1           8d
istio-network    deployment.apps/istio-ingressgateway             1/1     1            1           7m10s
istio-system     deployment.apps/istiod                           1/1     1            1           5h58m
kafka            deployment.apps/sample-cluster-entity-operator   1/1     1            1           15d
kafka            deployment.apps/strimzi-cluster-operator         1/1     1            1           22d
kube-system      deployment.apps/calico-kube-controllers          1/1     1            1           27d
kube-system      deployment.apps/coredns                          1/1     1            1           27d
kube-system      deployment.apps/hostpath-provisioner             1/1     1            1           24d
metallb-system   deployment.apps/controller                       1/1     1            1           24d

NAMESPACE        NAME                                                        DESIRED   CURRENT   READY   AGE
default          replicaset.apps/postgres-7f8c6b8ddf                         1         1         1       24d
default          replicaset.apps/sample-kubernetes-dbaccess-app-6f6dc66cb7   1         1         1       24d
default          replicaset.apps/service-mesh-app-service3-6bc878669c        1         1         1       8d
istio-network    replicaset.apps/istio-ingressgateway-78c65fbf55             1         1         1       7m10s
istio-system     replicaset.apps/istiod-5f7944b777                           1         1         1       5h58m
kafka            replicaset.apps/sample-cluster-entity-operator-dbdbbcb78    1         1         1       15d
kafka            replicaset.apps/strimzi-cluster-operator-6948497896         1         1         1       22d
kube-system      replicaset.apps/calico-kube-controllers-796fb75cc           1         1         1       27d
kube-system      replicaset.apps/coredns-5986966c54                          1         1         1       27d
kube-system      replicaset.apps/hostpath-provisioner-7c8bdf94b8             1         1         1       24d
metallb-system   replicaset.apps/controller-5484c5f99f                       1         1         1       24d

NAMESPACE   NAME                                      READY   AGE
default     statefulset.apps/keycloak-23              1/1     23d
default     statefulset.apps/keycloak-23-postgresql   1/1     23d

NAMESPACE       NAME                                                       REFERENCE                         TARGETS              MINPODS   MAXPODS   REPLICAS   AGE
istio-network   horizontalpodautoscaler.autoscaling/istio-ingressgateway   Deployment/istio-ingressgateway   cpu: <unknown>/80%   1         5         1          7m10s
istio-system    horizontalpodautoscaler.autoscaling/istiod                 Deployment/istiod                 cpu: <unknown>/80%   1         5         1          5h58m

```

**NOTE:** 上記のコマンド実行時には以下のyamlテンプレート(v1.23.0)が適用される。


```yaml
---
# Source: istio-microk8s/charts/istio-ingressgateway/templates/serviceaccount.yaml
apiVersion: v1
kind: ServiceAccount
metadata:
  name: istio-ingressgateway
  namespace: istio-network
  labels:
    helm.sh/chart: istio-ingressgateway-1.23.0
    app: "istio-ingressgateway"
    istio: "ingressgateway"
    app.kubernetes.io/name: istio-ingressgateway
    app.kubernetes.io/version: "1.23.0"
    app.kubernetes.io/managed-by: Helm
---
# Source: istio-microk8s/charts/istio-ingressgateway/templates/role.yaml
apiVersion: rbac.authorization.k8s.io/v1
kind: Role
metadata:
  name: istio-ingressgateway
  namespace: istio-network
  labels:
    helm.sh/chart: istio-ingressgateway-1.23.0
    app: "istio-ingressgateway"
    istio: "ingressgateway"
    app.kubernetes.io/name: istio-ingressgateway
    app.kubernetes.io/version: "1.23.0"
    app.kubernetes.io/managed-by: Helm
  annotations:
    {}
rules:
- apiGroups: [""]
  resources: ["secrets"]
  verbs: ["get", "watch", "list"]
---
# Source: istio-microk8s/charts/istio-ingressgateway/templates/role.yaml
apiVersion: rbac.authorization.k8s.io/v1
kind: RoleBinding
metadata:
  name: istio-ingressgateway
  namespace: istio-network
  labels:
    helm.sh/chart: istio-ingressgateway-1.23.0
    app: "istio-ingressgateway"
    istio: "ingressgateway"
    app.kubernetes.io/name: istio-ingressgateway
    app.kubernetes.io/version: "1.23.0"
    app.kubernetes.io/managed-by: Helm
  annotations:
    {}
roleRef:
  apiGroup: rbac.authorization.k8s.io
  kind: Role
  name: istio-ingressgateway
subjects:
- kind: ServiceAccount
  name: istio-ingressgateway
---
# Source: istio-microk8s/charts/istio-ingressgateway/templates/service.yaml
apiVersion: v1
kind: Service
metadata:
  name: istio-ingressgateway
  namespace: istio-network
  labels:
    helm.sh/chart: istio-ingressgateway-1.23.0
    app: "istio-ingressgateway"
    istio: "ingressgateway"
    app.kubernetes.io/name: istio-ingressgateway
    app.kubernetes.io/version: "1.23.0"
    app.kubernetes.io/managed-by: Helm
  annotations:
    {}
spec:
  type: LoadBalancer
  ports:
    - name: status-port
      port: 15021
      protocol: TCP
      targetPort: 15021
    - name: http2
      port: 80
      protocol: TCP
      targetPort: 80
    - name: https
      port: 443
      protocol: TCP
      targetPort: 443
  selector:
    app: "istio-ingressgateway"
    istio: "ingressgateway"
---
# Source: istio-microk8s/charts/istio-ingressgateway/templates/deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: istio-ingressgateway
  namespace: istio-network
  labels:
    helm.sh/chart: istio-ingressgateway-1.23.0
    app: "istio-ingressgateway"
    istio: "ingressgateway"
    app.kubernetes.io/name: istio-ingressgateway
    app.kubernetes.io/version: "1.23.0"
    app.kubernetes.io/managed-by: Helm
  annotations:
    {}
spec:
  selector:
    matchLabels:
      app: "istio-ingressgateway"
      istio: "ingressgateway"
  template:
    metadata:
      annotations:
        inject.istio.io/templates: gateway
        prometheus.io/path: /stats/prometheus
        prometheus.io/port: "15020"
        prometheus.io/scrape: "true"
        sidecar.istio.io/inject: "true"
      labels:
        sidecar.istio.io/inject: "true"
        app: "istio-ingressgateway"
        istio: "ingressgateway"
        app.kubernetes.io/name: istio-ingressgateway
        app.kubernetes.io/version: "1.23.0"
    spec:
      serviceAccountName: istio-ingressgateway
      securityContext:
        # Safe since 1.22: https://github.com/kubernetes/kubernetes/pull/103326
        sysctls:
        - name: net.ipv4.ip_unprivileged_port_start
          value: "0"
      containers:
        - name: istio-proxy
          # "auto" will be populated at runtime by the mutating webhook. See https://istio.io/latest/docs/setup/additional-setup/sidecar-injection/#customizing-injection
          image: auto
          securityContext:
            capabilities:
              drop:
              - ALL
            allowPrivilegeEscalation: false
            privileged: false
            readOnlyRootFilesystem: true
            runAsUser: 1337
            runAsGroup: 1337
            runAsNonRoot: true
          env:
          ports:
          - containerPort: 15090
            protocol: TCP
            name: http-envoy-prom
          resources:
            limits:
              cpu: 2000m
              memory: 1024Mi
            requests:
              cpu: 100m
              memory: 128Mi
      terminationGracePeriodSeconds: 30
---
# Source: istio-microk8s/charts/istio-ingressgateway/templates/hpa.yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: istio-ingressgateway
  namespace: istio-network
  labels:
    helm.sh/chart: istio-ingressgateway-1.23.0
    app: "istio-ingressgateway"
    istio: "ingressgateway"
    app.kubernetes.io/name: istio-ingressgateway
    app.kubernetes.io/version: "1.23.0"
    app.kubernetes.io/managed-by: Helm
  annotations:
    {}
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: istio-ingressgateway
  minReplicas: 1
  maxReplicas: 5
  metrics:
    - type: Resource
      resource:
        name: cpu
        target:
          averageUtilization: 80
          type: Utilization
---
# Source: istio-microk8s/charts/istio-ingressgateway/templates/zzz_profile.yaml
#  Flatten globals, if defined on a per-chart basis
```

- アプリケーションのデプロイ

Istio GatewayをYAML定義を作成する。

```yaml
apiVersion: networking.istio.io/v1alpha3
kind: Gateway
metadata:
  name: service-mesh-gateway
  namespace: istio-network
spec:
  selector:
    istio: ingressgateway
  servers:
    - port:
        number: 80
        name: http
        protocol: HTTP
      hosts:
        - "*"
```

*NOTE:* Istio Ingress GatewayとIstio Gatewayの違いはこちらの記事が分かりやすい。See: https://dev.to/vivekanandrapaka/istio-ingress-gateway-vs-istio-gateway-vs-kubernetes-ingress-5hgg

次に、Gatewayが参照するVirtualServiceのYAML定義を作成する。

```yaml
apiVersion: networking.istio.io/v1alpha3
kind: VirtualService
metadata:
  name: service-mesh
spec:
  hosts:
    - "*"
  gateways:
    - service-mesh-gateway
  http:
    - match:
        - uri:
            prefix: /service1
      route:
        - destination:
            host: service-mesh-app-service1
            port:
              number: 8080
    - match:
        - uri:
            prefix: /service2
      route:
        - destination:
            host: service-mesh-app-service2
            port:
              number: 8080
```

次に各Serviceに適用するアクセストークンを使用した認可処理をEnvoyプロキシに設定するための定義を作成する。
今回はService1のみ、アクセストークンが必要な設定を実装する。

```yaml
apiVersion: security.istio.io/v1beta1
kind: RequestAuthentication
metadata:
  name: ingress-jwt
  namespace: istio-network
spec:
  selector:
    matchLabels:
      app: service-mesh-app-service1
  jwtRules:
    - issuer: "http://keycloak-23/realms/master"
      jwksUri: "http://keycloak-23/realms/master/protocol/openid-connect/certs"
      fromHeaders:
        - name: Authorization
          prefix: "Bearer "
      forwardOriginalToken: true
---
apiVersion: security.istio.io/v1beta1
kind: AuthorizationPolicy
metadata:
  name: require-jwt
  namespace: istio-network
spec:
  selector:
    matchLabels:
      app: service-mesh-app-service1
  action: ALLOW
  rules:
    - when:
        - key: request.auth.claims[iss]
          values:
            - "http://keycloak-23/realms/master"
```

作成した各定義を、サービスメッシュネットワークに適用する。

```bash
$ microk8s kubectl apply -f gateway.yml -n istio-network
gateway.networking.istio.io/service-mesh-gateway created

$ microk8s kubectl apply -f virtualservice.yml -n istio-network
virtualservice.networking.istio.io/service-mesh created

$ microk8s kubectl apply -f authentication.yml -n istio-network
requestauthentication.security.istio.io/ingress-jwt created
authorizationpolicy.security.istio.io/require-jwt created

```

各マイクロサービスがクラスタ上で動作するように、環境依存ファイルを作成し、コンテナイメージおよびHelmパッケージを作成して各環境にプッシュする。

-- バックエンドサブネット 同期呼び出しされるマイクロサービス

Helmパッケージ作成のために、以下のファイルを作成する。

src/jkube/deployment.yml

```yaml
metadata:
  namespace: istio-network
spec:
  template:
    spec:
      serviceAccount: service-mesh-app-service2-serviceaccount
      containers:
        - env: # 環境変数設定
            - name: SPRING_PROFILES_ACTIVE
              value: "dev"
            - name: DB_URL
              value: "postgres.default:5432"
          imagePullPolicy: Always

```

src/jkube/role.yml

```yaml
apiVersion: rbac.authorization.k8s.io/v1
kind: ClusterRole
metadata:
  name: service-mesh-app-service2-clusterrole
  namespace: istio-network
  labels:
    app: service-mesh-app-service2
rules:
  - apiGroups: [""]
    resources:
      - namespaces
      - pods
      - replicationcontrollers
      - services
    verbs:
      - get
      - list
      - watch
```

src/jkube/rolebinding.yml

```yaml
apiVersion: rbac.authorization.k8s.io/v1
kind: ClusterRoleBinding
metadata:
  name: service-mesh-app-service2-clusterrolebinding
  namespace: istio-network
  labels:
    app: service-mesh-app-service1
roleRef:
  apiGroup: ""
  kind: ClusterRole
  name: service-mesh-app-service2-clusterrole
subjects:
  - kind: ServiceAccount
    name: service-mesh-app-service2-serviceaccount
    namespace: istio-network
```

src/jkube/service.yml

```yaml
metadata:
  namespace: istio-network
```

src/jkube/serviceaccount.yml

```yaml
apiVersion: v1
kind: ServiceAccount
metadata:
  name: service-mesh-app-service2-serviceaccount
  namespace: istio-network

```

作成が終わったら、プロジェクトのビルド・コンテナイメージ・Helmパッケージの作成を実行する。

```bash
$ ./mvnw clean package k8s:resource k8s:build k8s:helm k8s:helm-push k8s:push

[INFO] Scanning for projects...
[INFO]
[INFO] --------------< org.debugroom:service-mesh-app-service2 >---------------
[INFO] Building service-mesh-app-service2 0.0.1-SNAPSHOT
[INFO] --------------------------------[ jar ]---------------------------------
[INFO]
[INFO] --- maven-clean-plugin:3.3.2:clean (default-clean) @ service-mesh-app-service2 ---

// omit

[INFO] k8s: latest: digest: sha256:9b2481269728db97d861f413af8524f03be74202aa27aca0af1aef118335ba9f size: 1586
[INFO] k8s: Temporary image tag skipped. Target image 'debugroom/service-mesh-app-service2:latest' already has registry set or no registry is available
[INFO] k8s: Pushed debugroom/service-mesh-app-service2:latest in 7 seconds
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  22.611 s
[INFO] Finished at: 2024-08-22T21:31:44+09:00
[INFO] ------------------------------------------------------------------------

```

- バックエンドサブネット 同期呼び出し・非同期Producer型マイクロサービス

Ingress Controller(ロードバランサー)経由で、異なるマイクロサービスの呼び出しを行う。
WebClientで、Ingress ControllerのClusterIPを取得するよう設定する。

org.debugroom.sample.kubernetes.servicemesh.config.DevConfig

```java
package org.debugroom.sample.kubernetes.servicemesh.config;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.util.ClientBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.reactive.function.client.WebClient;

import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
@Profile("dev")
@Configuration
public class DevConfig {

    @Bean
    public WebClient service2WebClient() throws Exception{

        ApiClient apiClient = ClientBuilder.standard().build();
        CoreV1Api coreV1Api = new CoreV1Api(apiClient);
        List<V1Service> serviceList =
                coreV1Api.listServiceForAllNamespaces().execute().getItems();
        return WebClient.builder()
                .baseUrl("http://" + serviceList.stream().filter(
                        v1Service -> "istio-ingressgateway".equals(v1Service.getMetadata().getName())
                ).findFirst().get().getSpec().getClusterIP())
                .build();

    }

}
```

Kafkaに接続するための設定は、以下の通り、ネームスペースを指定して名前解決を行う。

src/main/resource/application-dev.yml

```yaml
service:
  service2:
    url: "" #This property is set by Dev configuration class.
spring:
  cloud:
    stream:
      kafka:
        binder:
          brokers:
            - sample-cluster-kafka-bootstrap.kafka
          auto-create-topics: false
      binders:
        output:
          destination: sample-topic
          content-type: application/json
          producer:
            partition-count: 1
```


Helmパッケージ作成のために、以下のファイルを作成する。

src/jkube/deployment.yml

```yaml
metadata:
  namespace: istio-network
spec:
  template:
    spec:
      serviceAccount: service-mesh-app-service1-serviceaccount
      containers:
        - env:
            - name: SPRING_PROFILES_ACTIVE
              value: "dev"
          imagePullPolicy: Always
```

src/jkube/role.yml

```yaml
apiVersion: rbac.authorization.k8s.io/v1
kind: ClusterRole
metadata:
  name: service-mesh-app-service1-clusterrole
  namespace: istio-network
  labels:
    app: service-mesh-app-service1
rules:
  - apiGroups: [""]
    resources:
      - namespaces
      - pods
      - replicationcontrollers
      - services
    verbs:
      - get
      - list
      - watch
```

src/jkube/rolebiding.yml

```yaml
apiVersion: rbac.authorization.k8s.io/v1
kind: ClusterRoleBinding
metadata:
  name: service-mesh-app-service1-clusterrolebinding
  namespace: istio-network
  labels:
    app: service-mesh-app-service1
roleRef:
  apiGroup: ""
  kind: ClusterRole
  name: service-mesh-app-service1-clusterrole
subjects:
  - kind: ServiceAccount
    name: service-mesh-app-service1-serviceaccount
    namespace: istio-network
```

src/jkube/service.yml

```yaml
metadata:
  namespace: istio-network
```

src/jkube/serviceaccount.yml

```yaml
apiVersion: v1
kind: ServiceAccount
metadata:
  name: service-mesh-app-service1-serviceaccount
  namespace: istio-network
```

作成が終わったら、プロジェクトのビルド・コンテナイメージ・Helmパッケージの作成を実行する。

```bash
$ ./mvnw clean package k8s:resource k8s:build k8s:helm k8s:helm-push k8s:push

[INFO] Scanning for projects...
[INFO]
[INFO] --------------< org.debugroom:service-mesh-app-service1 >---------------
[INFO] Building service-mesh-app-service1 0.0.1-SNAPSHOT
[INFO] --------------------------------[ jar ]---------------------------------
[INFO]
[INFO] --- maven-clean-plugin:3.3.2:clean (default-clean) @ service-mesh-app-service1 ---

// omit

[INFO] k8s: Temporary image tag skipped. Target image 'debugroom/service-mesh-app-service1:latest' already has registry set or no registry is available
[INFO] k8s: Pushed debugroom/service-mesh-app-service1:latest in 7 seconds
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  22.611 s
[INFO] Finished at: 2024-08-22T21:31:44+09:00
[INFO] ------------------------------------------------------------------------

```

- バックエンドサブネット 非同期Consumer型マイクロサービス

このタイプのサービスでは、リクエストのためのAPIをもたないため、サービスメッシュ外にデプロイするようHelmパッケージを構成する。

src/jkube/deployment.yml

```yaml
spec:
  template:
    spec:
      containers:
        - env: # 環境変数設定
          - name: SPRING_PROFILES_ACTIVE
            value: "dev"
          imagePullPolicy: Always
```

Kafkaに接続するための設定は、以下の通り、ネームスペースを指定して名前解決を行う。

src/main/resource/application-dev.yml

```yaml
spring:
  cloud:
    stream:
      kafka:
        binder:
          brokers:
            - sample-cluster-kafka-bootstrap.kafka
          auto-create-topics: false
      bindings:
        sampleConsumer-in-0:
          destination: sample-topic
      instance-count: 1
```

作成が終わったら、プロジェクトのビルド・コンテナイメージ・Helmパッケージの作成を実行する。


```bash
$ ./mvnw clean package k8s:resource k8s:build k8s:push k8s:helm k8s:helm-push

[INFO] Scanning for projects...
[INFO]
[INFO] --------------< org.debugroom:service-mesh-app-service3 >---------------
[INFO] Building service-mesh-app-service3 0.0.1-SNAPSHOT
[INFO]   from pom.xml
[INFO] --------------------------------[ jar ]---------------------------------
[INFO]
[INFO] --- clean:3.3.2:clean (default-clean) @ service-mesh-app-service3 ---

// omit

[INFO] --- k8s:1.17.0:helm-push (default-cli) @ service-mesh-app-service3 ---
[INFO] k8s: Uploading Helm Chart "service-mesh-app-service3" to sample-chartmuseum-snapshot-repository
[INFO] k8s: Upload Successful
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  23.967 s
[INFO] Finished at: 2024-08-22T22:46:47+09:00
[INFO] ------------------------------------------------------------------------

```

- フロントサブネット アプリケーションの実装

フロントサブネットでは、サービスメッシュに組み込まず、アクセス可能なように構成する。
Ingress Controllerを経由せずにここではバックエンドサービスにアクセスする仕様とする。

src/main/resource/application-dev.yml

```yaml
spring:
  security:
    oauth2:
      client:
        provider:
          keycloak:
            authorizationUri: http://XXX.XXX.XXX.XXX:7000/realms/master/protocol/openid-connect/auth
            tokenUri: http://XXX.XXX.XXX.XXX:7000/realms/master/protocol/openid-connect/token
            userInfoUri: http://XXX.XXX.XXX.XXX:7000/realms/master/protocol/openid-connect/userinfo
            jwkSetUri: http://XXX.XXX.XXX.XXX:7000/realms/master/protocol/openid-connect/certs
            userNameAttribute: preferred_username
            issuer-uri: http://XXX.XXX.XXX.XXX:7000/realms/master
        registration:
          keycloak:
#            redirectUri: '{baseUrl}/login/oauth2/code/{registrationId}'
#            redirectUri: 'http://service-mesh-webapp/login/oauth2/code/keycloak'
            redirectUri: 'http://XXX.XXX.XXX.XXX:8090/login/oauth2/code/keycloak'
            clientId: service_mesh_app_dev
            clientSecret: YYYYYYYYYYYYYYYYYYYYYYYYYYYYY
service:
  service1:
    url: http://service-mesh-app-service1.istio-network:8080
  service2:
    url: http://service-mesh-app-service2.istio-network:8080

```

src/jkube/deployment.yml

```bash
spec:
  template:
    spec:
      containers:
        - env:
            - name: SPRING_PROFILES_ACTIVE
              value: "dev"
          imagePullPolicy: Always
```

-- 各サービス・アプリケーションのデプロイ

```bash
$ microk8s helm3 install service-mesh-app-service1 chartmuseum/service-mesh-app-service1 --devel
NAME: service-mesh-app-service1
LAST DEPLOYED: Thu Aug 22 11:10:24 2024
NAMESPACE: default
STATUS: deployed
REVISION: 1
TEST SUITE: None

$ microk8s helm3 install service-mesh-app-service2 chartmuseum/service-mesh-app-service2 --devel
NAME: service-mesh-app-service2
LAST DEPLOYED: Thu Aug 22 12:31:54 2024
NAMESPACE: default
STATUS: deployed
REVISION: 1
TEST SUITE: None

$ microk8s helm3 install service-mesh-app-service3 chartmuseum/service-mesh-app-service3 --devel
NAME: service-mesh-app-service3
LAST DEPLOYED: Thu Aug 22 14:33:08 2024
NAMESPACE: default
STATUS: deployed
REVISION: 1
TEST SUITE: None

$ microk8s helm3 install service-mesh-webapp chartmuseum/service-mesh-webapp --devel
NAME: service-mesh-webapp
LAST DEPLOYED: Thu Aug 22 14:17:05 2024
NAMESPACE: default
STATUS: deployed
REVISION: 1
TEST SUITE: None

$ microk8s kubectl expose deployment service-mesh-webapp --type=LoadBalancer --port=8080 --name=service-mesh-webapp-lb
service/service-mesh-webapp-lb exposed


$ microk8s kubectl get all --all-namespaces
NAMESPACE        NAME                                                  READY   STATUS    RESTARTS      AGE
default          pod/keycloak-23-0                                     1/1     Running   0             24d
default          pod/keycloak-23-postgresql-0                          1/1     Running   0             24d
default          pod/postgres-7f8c6b8ddf-9j6tx                         1/1     Running   0             25d
default          pod/sample-kubernetes-dbaccess-app-6f6dc66cb7-glbwj   1/1     Running   0             25d
default          pod/service-mesh-app-service3-679d8bc69d-n2cbv        1/1     Running   0             41s
default          pod/service-mesh-webapp-58644967df-5ccjc              1/1     Running   0             16m
istio-network    pod/istio-ingressgateway-78c65fbf55-9tck8             1/1     Running   0             30h
istio-network    pod/service-mesh-app-service1-d76ccfc49-qb9fj         2/2     Running   0             3h23m
istio-network    pod/service-mesh-app-service2-78c65695b8-fzvkh        2/2     Running   0             121m
istio-system     pod/istiod-5f7944b777-gxt5k                           1/1     Running   0             36h
kafka            pod/sample-cluster-entity-operator-dbdbbcb78-46ss8    2/2     Running   0             16d
kafka            pod/sample-cluster-kafka-0                            1/1     Running   0             16d
kafka            pod/sample-cluster-zookeeper-0                        1/1     Running   0             16d
kafka            pod/strimzi-cluster-operator-6948497896-4wfdj         1/1     Running   0             23d
kube-system      pod/calico-kube-controllers-796fb75cc-lh9kb           1/1     Running   0             28d
kube-system      pod/calico-node-zxvfd                                 1/1     Running   0             23d
kube-system      pod/coredns-5986966c54-2fm58                          1/1     Running   0             28d
kube-system      pod/hostpath-provisioner-7c8bdf94b8-s4b45             1/1     Running   1 (23d ago)   25d
metallb-system   pod/controller-5484c5f99f-xlcsf                       1/1     Running   0             25d
metallb-system   pod/speaker-2vpvq                                     1/1     Running   0             25d

NAMESPACE        NAME                                              TYPE           CLUSTER-IP       EXTERNAL-IP   PORT(S)                                        AGE
default          service/keycloak-23                               ClusterIP      10.152.183.67    <none>        80/TCP                                         24d
default          service/keycloak-23-headless                      ClusterIP      None             <none>        8080/TCP                                       24d
default          service/keycloak-23-postgresql                    ClusterIP      10.152.183.227   <none>        5432/TCP                                       24d
default          service/keycloak-23-postgresql-hl                 ClusterIP      None             <none>        5432/TCP                                       24d
default          service/kubernetes                                ClusterIP      10.152.183.1     <none>        443/TCP                                        28d
default          service/postgres                                  NodePort       10.152.183.56    <none>        5432:30432/TCP                                 25d
default          service/sample-kubernetes-dbaccess-app            ClusterIP      10.152.183.180   <none>        8080/TCP                                       25d
default          service/sample-kubernetes-dbaccess-app-lb         LoadBalancer   10.152.183.196   192.168.1.0   8080:31570/TCP                                 25d
default          service/service-mesh-app-service3                 ClusterIP      10.152.183.24    <none>        8080/TCP                                       41s
default          service/service-mesh-webapp                       ClusterIP      10.152.183.121   <none>        8080/TCP                                       16m
default          service/service-mesh-webapp-lb                    LoadBalancer   10.152.183.52    192.168.1.4   8080:31051/TCP                                 9m33s
istio-network    service/istio-ingressgateway                      LoadBalancer   10.152.183.99    192.168.1.3   15021:32164/TCP,80:30821/TCP,443:30554/TCP     30h
istio-network    service/service-mesh-app-service1                 ClusterIP      10.152.183.126   <none>        8080/TCP                                       3h23m
istio-network    service/service-mesh-app-service2                 ClusterIP      10.152.183.120   <none>        8080/TCP                                       121m
istio-system     service/istiod                                    ClusterIP      10.152.183.58    <none>        15010/TCP,15012/TCP,443/TCP,15014/TCP          36h
kafka            service/sample-cluster-kafka-0                    LoadBalancer   10.152.183.95    192.168.1.2   9094:31141/TCP                                 16d
kafka            service/sample-cluster-kafka-bootstrap            ClusterIP      10.152.183.165   <none>        9091/TCP,9092/TCP,9093/TCP                     16d
kafka            service/sample-cluster-kafka-brokers              ClusterIP      None             <none>        9090/TCP,9091/TCP,8443/TCP,9092/TCP,9093/TCP   16d
kafka            service/sample-cluster-kafka-external-bootstrap   LoadBalancer   10.152.183.70    192.168.1.1   9094:30113/TCP                                 16d
kafka            service/sample-cluster-zookeeper-client           ClusterIP      10.152.183.45    <none>        2181/TCP                                       16d
kafka            service/sample-cluster-zookeeper-nodes            ClusterIP      None             <none>        2181/TCP,2888/TCP,3888/TCP                     16d
kube-system      service/kube-dns                                  ClusterIP      10.152.183.10    <none>        53/UDP,53/TCP,9153/TCP                         28d
metallb-system   service/webhook-service                           ClusterIP      10.152.183.57    <none>        443/TCP                                        25d

NAMESPACE        NAME                         DESIRED   CURRENT   READY   UP-TO-DATE   AVAILABLE   NODE SELECTOR            AGE
kube-system      daemonset.apps/calico-node   1         1         1       1            1           kubernetes.io/os=linux   28d
metallb-system   daemonset.apps/speaker       1         1         1       1            1           kubernetes.io/os=linux   25d

NAMESPACE        NAME                                             READY   UP-TO-DATE   AVAILABLE   AGE
default          deployment.apps/postgres                         1/1     1            1           25d
default          deployment.apps/sample-kubernetes-dbaccess-app   1/1     1            1           25d
default          deployment.apps/service-mesh-app-service3        1/1     1            1           41s
default          deployment.apps/service-mesh-webapp              1/1     1            1           16m
istio-network    deployment.apps/istio-ingressgateway             1/1     1            1           30h
istio-network    deployment.apps/service-mesh-app-service1        1/1     1            1           3h23m
istio-network    deployment.apps/service-mesh-app-service2        1/1     1            1           121m
istio-system     deployment.apps/istiod                           1/1     1            1           36h
kafka            deployment.apps/sample-cluster-entity-operator   1/1     1            1           16d
kafka            deployment.apps/strimzi-cluster-operator         1/1     1            1           23d
kube-system      deployment.apps/calico-kube-controllers          1/1     1            1           28d
kube-system      deployment.apps/coredns                          1/1     1            1           28d
kube-system      deployment.apps/hostpath-provisioner             1/1     1            1           25d
metallb-system   deployment.apps/controller                       1/1     1            1           25d

NAMESPACE        NAME                                                        DESIRED   CURRENT   READY   AGE
default          replicaset.apps/postgres-7f8c6b8ddf                         1         1         1       25d
default          replicaset.apps/sample-kubernetes-dbaccess-app-6f6dc66cb7   1         1         1       25d
default          replicaset.apps/service-mesh-app-service3-679d8bc69d        1         1         1       41s
default          replicaset.apps/service-mesh-webapp-58644967df              1         1         1       16m
istio-network    replicaset.apps/istio-ingressgateway-78c65fbf55             1         1         1       30h
istio-network    replicaset.apps/service-mesh-app-service1-d76ccfc49         1         1         1       3h23m
istio-network    replicaset.apps/service-mesh-app-service2-78c65695b8        1         1         1       121m
istio-system     replicaset.apps/istiod-5f7944b777                           1         1         1       36h
kafka            replicaset.apps/sample-cluster-entity-operator-dbdbbcb78    1         1         1       16d
kafka            replicaset.apps/strimzi-cluster-operator-6948497896         1         1         1       23d
kube-system      replicaset.apps/calico-kube-controllers-796fb75cc           1         1         1       28d
kube-system      replicaset.apps/coredns-5986966c54                          1         1         1       28d
kube-system      replicaset.apps/hostpath-provisioner-7c8bdf94b8             1         1         1       25d
metallb-system   replicaset.apps/controller-5484c5f99f                       1         1         1       25d

NAMESPACE   NAME                                      READY   AGE
default     statefulset.apps/keycloak-23              1/1     24d
default     statefulset.apps/keycloak-23-postgresql   1/1     24d

NAMESPACE       NAME                                                       REFERENCE                         TARGETS              MINPODS   MAXPODS   REPLICAS   AGE
istio-network   horizontalpodautoscaler.autoscaling/istio-ingressgateway   Deployment/istio-ingressgateway   cpu: <unknown>/80%   1         5         1          30h
istio-system    horizontalpodautoscaler.autoscaling/istiod                 Deployment/istiod                 cpu: <unknown>/80%   1         5         1          36h

$ microk8s kubectl port-forward service/service-mesh-webapp-lb 80:8080 --address 0.0.0.0 &
```

--動作確認

クラスタのあるインスタンスから各サービスが動作するか確認する。

--- バックエンドマイクロサービスから、別のマイクロサービスの同期呼び出し

```bash
$ microk8s kubectl run --image=debugroom/test-client:latest --restart=Never --rm -i testpod --command -- curl http://service-mesh-app-service2.istio-network:8080/service2/sample -L
  % Total    % Received % Xferd  Average Speed   Time    Time     Time  Current
                                 Dload  Upload   Total   Spent    Left  Speed
100    39    0    39    0     0   4941      0 --:--:-- --:--:-- --:--:--  5571

{"text":"This is created by Service2."}

pod "testpod" deleted

$ microk8s kubectl run --image=debugroom/test-client:latest --restart=Never --rm -i testpod --command -- curl http://service-mesh-app-service1:8080/service1/test -H "Authorization : Bearer eyJhbGciOiJSUzI1NiIsInR5cCIgOiAiSldUIiwia2lkIiA6ICJaaFVUalVPLThIM1UyZ251T1l3dTluM25henZ6TXhJN3ZkN195clE3a0RJIn0.eyJleHAiOjE3MjQ2MzQ1NzUsImlhdCI6MTcyNDYzNDUxNSwiYXV0aF90aW1lIjoxNzI0NjM0NTE1LCJqdGkiOiJiMTFmZjc5NS02ZDhjLTRiNjMtYTg0Ny1hMGFjNTQ1NTZlYWIiLCJpc3MiOiJodHRwOi8vMTMuMjMxLjEyNC4xMzg6NzAwMC9yZWFsbXMvbWFzdGVyIiwiYXVkIjpbIm1hc3Rlci1yZWFsbSIsImFjY291bnQiXSwic3ViIjoiOWU3NjliMWQtNjk3Zi00ZDlmLWE3NzMtNTc4MGE5ZDQwODQwIiwidHlwIjoiQmVhcmVyIiwiYXpwIjoic2VydmljZV9tZXNoX2FwcF9kZXYiLCJub25jZSI6Ik9VTDViMmJYamtuSDBPZUNKaGoyWUZZeml4Tlh2M2VOTmZ3SEtCTUJjeGMiLCJzZXNzaW9uX3N0YXRlIjoiNzg0M2YwY2QtNGQ3NS00OGI0LTljZGYtODFlODRhYzFhOWI4IiwiYWNyIjoiMSIsImFsbG93ZWQtb3JpZ2lucyI6WyJodHRwOi8vMTMuMjMxLjEyNC4xMzg6ODA5MCJdLCJyZWFsbV9hY2Nlc3MiOnsicm9sZXMiOlsiY3JlYXRlLXJlYWxtIiwiZGVmYXVsdC1yb2xlcy1tYXN0ZXIiLCJvZmZsaW5lX2FjY2VzcyIsImFkbWluIiwidW1hX2F1dGhvcml6YXRpb24iXX0sInJlc291cmNlX2FjY2VzcyI6eyJtYXN0ZXItcmVhbG0iOnsicm9sZXMiOlsidmlldy1yZWFsbSIsInZpZXctaWRlbnRpdHktcHJvdmlkZXJzIiwibWFuYWdlLWlkZW50aXR5LXByb3ZpZGVycyIsImltcGVyc29uYXRpb24iLCJjcmVhdGUtY2xpZW50IiwibWFuYWdlLXVzZXJzIiwicXVlcnktcmVhbG1zIiwidmlldy1hdXRob3JpemF0aW9uIiwicXVlcnktY2xpZW50cyIsInF1ZXJ5LXVzZXJzIiwibWFuYWdlLWV2ZW50cyIsIm1hbmFnZS1yZWFsbSIsInZpZXctZXZlbnRzIiwidmlldy11c2VycyIsInZpZXctY2xpZW50cyIsIm1hbmFnZS1hdXRob3JpemF0aW9uIiwibWFuYWdlLWNsaWVudHMiLCJxdWVyeS1ncm91cHMiXX0sImFjY291bnQiOnsicm9sZXMiOlsibWFuYWdlLWFjY291bnQiLCJtYW5hZ2UtYWNjb3VudC1saW5rcyIsInZpZXctcHJvZmlsZSJdfX0sInNjb3BlIjoib3BlbmlkIHByb2ZpbGUgZW1haWwiLCJzaWQiOiI3ODQzZjBjZC00ZDc1LTQ4YjQtOWNkZi04MWU4NGFjMWE5YjgiLCJlbWFpbF92ZXJpZmllZCI6ZmFsc2UsInByZWZlcnJlZF91c2VybmFtZSI6InVzZXIifQ.hR3nlrW9SxanbtLeCXJ-LBblMmahAA8Ws0R4EGHhns0FtW_Bl1vamQozo1lKViwuZUorUu9bT37YlDODTRKiAY8vY7dO4hL4Z2qVr3peZaabbpww8mptucCElTMrCP-2jQB74wW8bMNNMT3OlqCQJWY-BuM4wb3aHdCVNYuvAbv-i6uuV8P1Uu2WIiIh4LEF-j2HOCfrTvY6iOflUPHbyzT6dLji8AFSlPEX5V6JA89QMLf3kb5PD1z9DEC7eGR2LBsdCBquXqdDeHbW84yCqwaZgNxhIqm6CfkedSuQWeSb40RQYeg4yBfipnHEJrASjNxcuq-pm2c2JsckGt1fjw"
  % Total    % Received % Xferd  Average Speed   Time    Time     Time  Current
                                 Dload  Upload   Total   Spent    Left  Speed
  0     0    0     0    0     0      0      0 --:--:-- --:--:-- --:--:--     0curl: (6) Could not resolve host: service-mesh-app-service1; Unknown error
pod "testpod" deleted
pod default/testpod terminated (Error)

```

TODO:

* トークン付与した認可処理の確認
* ネームスペースやサービスごとのトランザクション境界の説明



<!--

MicroK8s上でIstioを有効化する。

```bash
$ git config --global --add safe.directory /snap/microk8s/current/addons/community/.git
$ microk8s enable community
Infer repository core for addon community
Cloning into '/var/snap/microk8s/common/addons/community'...
done.
Community repository is now enabled

$ microk8s enable istio
Infer repository community for addon istio
Enabling Istio
Fetching istioctl version v1.18.2.
  % Total    % Received % Xferd  Average Speed   Time    Time     Time  Current
                                 Dload  Upload   Total   Spent    Left  Speed
  0     0    0     0    0     0      0      0 --:--:-- --:--:-- --:--:--     0
100 26.6M  100 26.6M    0     0  18.1M      0  0:00:01  0:00:01 --:--:-- 70.6M
istio-1.18.2/
istio-1.18.2/README.md
istio-1.18.2/manifest.yaml
istio-1.18.2/samples/
istio-1.18.2/samples/bookinfo/
istio-1.18.2/samples/bookinfo/build_push_update_images.sh
istio-1.18.2/samples/bookinfo/README.md
istio-1.18.2/samples/bookinfo/networking/
istio-1.18.2/samples/bookinfo/networking/destination-rule-reviews.yaml
istio-1.18.2/samples/bookinfo/networking/virtual-service-reviews-test-v2.yaml
istio-1.18.2/samples/bookinfo/networking/virtual-service-reviews-50-v3.yaml
istio-1.18.2/samples/bookinfo/networking/virtual-service-ratings-mysql-vm.yaml
istio-1.18.2/samples/bookinfo/networking/virtual-service-reviews-90-10.yaml
istio-1.18.2/samples/bookinfo/networking/virtual-service-ratings-test-abort.yaml
istio-1.18.2/samples/bookinfo/networking/virtual-service-reviews-jason-v2-v3.yaml
istio-1.18.2/samples/bookinfo/networking/virtual-service-reviews-v2-v3.yaml
istio-1.18.2/samples/bookinfo/networking/virtual-service-ratings-db.yaml
istio-1.18.2/samples/bookinfo/networking/egress-rule-google-apis.yaml
istio-1.18.2/samples/bookinfo/networking/virtual-service-ratings-test-delay.yaml
istio-1.18.2/samples/bookinfo/networking/virtual-service-all-v1.yaml
istio-1.18.2/samples/bookinfo/networking/destination-rule-all-mtls.yaml
istio-1.18.2/samples/bookinfo/networking/fault-injection-details-v1.yaml
istio-1.18.2/samples/bookinfo/networking/virtual-service-ratings-mysql.yaml
istio-1.18.2/samples/bookinfo/networking/virtual-service-details-v2.yaml
istio-1.18.2/samples/bookinfo/networking/certmanager-gateway.yaml
istio-1.18.2/samples/bookinfo/networking/virtual-service-reviews-80-20.yaml
istio-1.18.2/samples/bookinfo/networking/destination-rule-all.yaml
istio-1.18.2/samples/bookinfo/networking/virtual-service-reviews-v3.yaml
istio-1.18.2/samples/bookinfo/networking/bookinfo-gateway.yaml
istio-1.18.2/samples/bookinfo/platform/
istio-1.18.2/samples/bookinfo/platform/kube/
istio-1.18.2/samples/bookinfo/platform/kube/cleanup.sh
istio-1.18.2/samples/bookinfo/platform/kube/bookinfo-ratings-v2.yaml
istio-1.18.2/samples/bookinfo/platform/kube/bookinfo-ratings.yaml
istio-1.18.2/samples/bookinfo/platform/kube/bookinfo-versions.yaml
istio-1.18.2/samples/bookinfo/platform/kube/bookinfo-ratings-discovery.yaml
istio-1.18.2/samples/bookinfo/platform/kube/bookinfo-psa.yaml
istio-1.18.2/samples/bookinfo/platform/kube/bookinfo.yaml
istio-1.18.2/samples/bookinfo/platform/kube/productpage-nodeport.yaml
istio-1.18.2/samples/bookinfo/platform/kube/bookinfo-db.yaml
istio-1.18.2/samples/bookinfo/platform/kube/bookinfo-ingress.yaml
istio-1.18.2/samples/bookinfo/platform/kube/bookinfo-certificate.yaml
istio-1.18.2/samples/bookinfo/platform/kube/README.md
istio-1.18.2/samples/bookinfo/platform/kube/bookinfo-details.yaml
istio-1.18.2/samples/bookinfo/platform/kube/bookinfo-ratings-v2-mysql-vm.yaml
istio-1.18.2/samples/bookinfo/platform/kube/bookinfo-reviews-v2.yaml
istio-1.18.2/samples/bookinfo/platform/kube/bookinfo-details-v2.yaml
istio-1.18.2/samples/bookinfo/platform/kube/bookinfo-ratings-v2-mysql.yaml
istio-1.18.2/samples/bookinfo/platform/kube/bookinfo-mysql.yaml
istio-1.18.2/samples/bookinfo/gateway-api/
istio-1.18.2/samples/bookinfo/gateway-api/route-reviews-v3.yaml
istio-1.18.2/samples/bookinfo/gateway-api/route-all-v1.yaml
istio-1.18.2/samples/bookinfo/gateway-api/route-reviews-90-10.yaml
istio-1.18.2/samples/bookinfo/gateway-api/route-reviews-v1.yaml
istio-1.18.2/samples/bookinfo/gateway-api/route-reviews-50-v3.yaml
istio-1.18.2/samples/bookinfo/gateway-api/bookinfo-gateway.yaml
istio-1.18.2/samples/bookinfo/policy/
istio-1.18.2/samples/bookinfo/policy/productpage_envoy_ratelimit.yaml
istio-1.18.2/samples/bookinfo/src/
istio-1.18.2/samples/bookinfo/src/build-services.sh
istio-1.18.2/samples/bookinfo/src/ratings/
istio-1.18.2/samples/bookinfo/src/ratings/package.json
istio-1.18.2/samples/bookinfo/src/details/
istio-1.18.2/samples/bookinfo/src/productpage/
istio-1.18.2/samples/bookinfo/src/productpage/templates/
istio-1.18.2/samples/bookinfo/src/productpage/static/
istio-1.18.2/samples/bookinfo/src/productpage/static/bootstrap/
istio-1.18.2/samples/bookinfo/src/productpage/static/bootstrap/js/
istio-1.18.2/samples/bookinfo/src/productpage/static/bootstrap/fonts/
istio-1.18.2/samples/bookinfo/src/productpage/static/bootstrap/css/
istio-1.18.2/samples/bookinfo/src/productpage/requirements.txt
istio-1.18.2/samples/bookinfo/src/productpage/test-requirements.txt
istio-1.18.2/samples/bookinfo/src/productpage/tests/
istio-1.18.2/samples/bookinfo/src/productpage/tests/unit/
istio-1.18.2/samples/bookinfo/src/mongodb/
istio-1.18.2/samples/bookinfo/src/mongodb/ratings_data.json
istio-1.18.2/samples/bookinfo/src/mongodb/script.sh
istio-1.18.2/samples/bookinfo/src/reviews/
istio-1.18.2/samples/bookinfo/src/reviews/reviews-wlpcfg/
istio-1.18.2/samples/bookinfo/src/reviews/reviews-wlpcfg/servers/
istio-1.18.2/samples/bookinfo/src/reviews/reviews-wlpcfg/servers/LibertyProjectServer/
istio-1.18.2/samples/bookinfo/src/reviews/reviews-wlpcfg/shared/
istio-1.18.2/samples/bookinfo/src/reviews/reviews-wlpcfg/src/
istio-1.18.2/samples/bookinfo/src/reviews/reviews-wlpcfg/src/test/
istio-1.18.2/samples/bookinfo/src/reviews/reviews-wlpcfg/src/test/java/
istio-1.18.2/samples/bookinfo/src/reviews/reviews-wlpcfg/src/test/java/it/
istio-1.18.2/samples/bookinfo/src/reviews/reviews-wlpcfg/src/test/java/it/rest/
istio-1.18.2/samples/bookinfo/src/reviews/reviews-application/
istio-1.18.2/samples/bookinfo/src/reviews/reviews-application/src/
istio-1.18.2/samples/bookinfo/src/reviews/reviews-application/src/test/
istio-1.18.2/samples/bookinfo/src/reviews/reviews-application/src/test/java/
istio-1.18.2/samples/bookinfo/src/reviews/reviews-application/src/test/java/test/
istio-1.18.2/samples/bookinfo/src/reviews/reviews-application/src/main/
istio-1.18.2/samples/bookinfo/src/reviews/reviews-application/src/main/webapp/
istio-1.18.2/samples/bookinfo/src/reviews/reviews-application/src/main/webapp/WEB-INF/
istio-1.18.2/samples/bookinfo/src/reviews/reviews-application/src/main/java/
istio-1.18.2/samples/bookinfo/src/reviews/reviews-application/src/main/java/application/
istio-1.18.2/samples/bookinfo/src/reviews/reviews-application/src/main/java/application/rest/
istio-1.18.2/samples/bookinfo/src/mysql/
istio-1.18.2/samples/bookinfo/swagger.yaml
istio-1.18.2/samples/bookinfo/demo-profile-no-gateways.yaml
istio-1.18.2/samples/open-telemetry/
istio-1.18.2/samples/open-telemetry/loki/
istio-1.18.2/samples/open-telemetry/loki/REAME.md
istio-1.18.2/samples/open-telemetry/loki/iop.yaml
istio-1.18.2/samples/open-telemetry/loki/otel.yaml
istio-1.18.2/samples/open-telemetry/loki/telemetry.yaml
istio-1.18.2/samples/open-telemetry/als/
istio-1.18.2/samples/open-telemetry/als/README.md
istio-1.18.2/samples/open-telemetry/tracing/
istio-1.18.2/samples/open-telemetry/tracing/README.md
istio-1.18.2/samples/open-telemetry/tracing/telemetry.yaml
istio-1.18.2/samples/open-telemetry/otel.yaml
istio-1.18.2/samples/websockets/
istio-1.18.2/samples/websockets/README.md
istio-1.18.2/samples/websockets/app.yaml
istio-1.18.2/samples/websockets/route.yaml
istio-1.18.2/samples/extauthz/
istio-1.18.2/samples/extauthz/cmd/
istio-1.18.2/samples/extauthz/cmd/extauthz/
istio-1.18.2/samples/extauthz/README.md
istio-1.18.2/samples/extauthz/local-ext-authz.yaml
istio-1.18.2/samples/extauthz/docker/
istio-1.18.2/samples/extauthz/ext-authz.yaml
istio-1.18.2/samples/helloworld/
istio-1.18.2/samples/helloworld/helloworld.yaml
istio-1.18.2/samples/helloworld/helloworld-gateway.yaml
istio-1.18.2/samples/helloworld/README.md
istio-1.18.2/samples/helloworld/gateway-api/
istio-1.18.2/samples/helloworld/gateway-api/helloworld-versions.yaml
istio-1.18.2/samples/helloworld/gateway-api/helloworld-route.yaml
istio-1.18.2/samples/helloworld/gateway-api/helloworld-gateway.yaml
istio-1.18.2/samples/helloworld/gateway-api/README.md
istio-1.18.2/samples/helloworld/gen-helloworld.sh
istio-1.18.2/samples/helloworld/src/
istio-1.18.2/samples/helloworld/src/build_service.sh
istio-1.18.2/samples/helloworld/src/requirements.txt
istio-1.18.2/samples/helloworld/loadgen.sh
istio-1.18.2/samples/ratelimit/
istio-1.18.2/samples/ratelimit/rate-limit-service.yaml
istio-1.18.2/samples/addons/
istio-1.18.2/samples/addons/grafana.yaml
istio-1.18.2/samples/addons/prometheus.yaml
istio-1.18.2/samples/addons/jaeger.yaml
istio-1.18.2/samples/addons/extras/
istio-1.18.2/samples/addons/extras/prometheus_vm_tls.yaml
istio-1.18.2/samples/addons/extras/prometheus-operator.yaml
istio-1.18.2/samples/addons/extras/prometheus_vm.yaml
istio-1.18.2/samples/addons/extras/zipkin.yaml
istio-1.18.2/samples/addons/extras/skywalking.yaml
istio-1.18.2/samples/addons/loki.yaml
istio-1.18.2/samples/addons/README.md
istio-1.18.2/samples/addons/kiali.yaml
istio-1.18.2/samples/security/
istio-1.18.2/samples/security/spire/
istio-1.18.2/samples/security/spire/sleep-spire.yaml
istio-1.18.2/samples/security/spire/README.md
istio-1.18.2/samples/security/spire/istio-spire-config.yaml
istio-1.18.2/samples/security/spire/spire-quickstart.yaml
istio-1.18.2/samples/security/spire/clusterspiffeid.yaml
istio-1.18.2/samples/security/psp/
istio-1.18.2/samples/security/psp/sidecar-psp.yaml
istio-1.18.2/samples/jwt-server/
istio-1.18.2/samples/jwt-server/testdata/
istio-1.18.2/samples/jwt-server/jwt-server.yaml
istio-1.18.2/samples/jwt-server/src/
istio-1.18.2/samples/jwt-server/src/Makefile
istio-1.18.2/samples/README.md
istio-1.18.2/samples/cicd/
istio-1.18.2/samples/cicd/skaffold/
istio-1.18.2/samples/cicd/skaffold/README.md
istio-1.18.2/samples/cicd/skaffold/skaffold.yaml
istio-1.18.2/samples/tcp-echo/
istio-1.18.2/samples/tcp-echo/tcp-echo-20-v2.yaml
istio-1.18.2/samples/tcp-echo/tcp-echo-dual-stack.yaml
istio-1.18.2/samples/tcp-echo/tcp-echo-ipv6.yaml
istio-1.18.2/samples/tcp-echo/README.md
istio-1.18.2/samples/tcp-echo/tcp-echo-all-v1.yaml
istio-1.18.2/samples/tcp-echo/gateway-api/
istio-1.18.2/samples/tcp-echo/gateway-api/tcp-echo-20-v2.yaml
istio-1.18.2/samples/tcp-echo/gateway-api/tcp-echo-all-v1.yaml
istio-1.18.2/samples/tcp-echo/tcp-echo-services.yaml
istio-1.18.2/samples/tcp-echo/src/
istio-1.18.2/samples/tcp-echo/tcp-echo-ipv4.yaml
istio-1.18.2/samples/tcp-echo/tcp-echo.yaml
istio-1.18.2/samples/multicluster/
istio-1.18.2/samples/multicluster/gen-eastwest-gateway.sh
istio-1.18.2/samples/multicluster/expose-istiod.yaml
istio-1.18.2/samples/multicluster/README.md
istio-1.18.2/samples/multicluster/expose-services.yaml
istio-1.18.2/samples/multicluster/expose-istiod-https.yaml
istio-1.18.2/samples/custom-bootstrap/
istio-1.18.2/samples/custom-bootstrap/example-app.yaml
istio-1.18.2/samples/custom-bootstrap/README.md
istio-1.18.2/samples/custom-bootstrap/custom-bootstrap.yaml
istio-1.18.2/samples/httpbin/
istio-1.18.2/samples/httpbin/httpbin.yaml
istio-1.18.2/samples/httpbin/httpbin-nodeport.yaml
istio-1.18.2/samples/httpbin/httpbin-vault.yaml
istio-1.18.2/samples/httpbin/README.md
istio-1.18.2/samples/httpbin/httpbin-gateway.yaml
istio-1.18.2/samples/httpbin/gateway-api/
istio-1.18.2/samples/httpbin/gateway-api/httpbin-gateway.yaml
istio-1.18.2/samples/httpbin/sample-client/
istio-1.18.2/samples/httpbin/sample-client/fortio-deploy.yaml
istio-1.18.2/samples/wasm_modules/
istio-1.18.2/samples/wasm_modules/README.md
istio-1.18.2/samples/wasm_modules/header_injector/
istio-1.18.2/samples/wasm_modules/header_injector/Makefile
istio-1.18.2/samples/external/
istio-1.18.2/samples/external/pypi.yaml
istio-1.18.2/samples/external/README.md
istio-1.18.2/samples/external/github.yaml
istio-1.18.2/samples/external/aptget.yaml
istio-1.18.2/samples/kind-lb/
istio-1.18.2/samples/kind-lb/setupkind.sh
istio-1.18.2/samples/kind-lb/README.md
istio-1.18.2/samples/operator/
istio-1.18.2/samples/operator/values-pilot.yaml
istio-1.18.2/samples/operator/pilot-k8s.yaml
istio-1.18.2/samples/operator/cni-on.yaml
istio-1.18.2/samples/operator/values-global.yaml
istio-1.18.2/samples/operator/default-install.yaml
istio-1.18.2/samples/operator/pilot-advanced-override.yaml
istio-1.18.2/samples/grpc-echo/
istio-1.18.2/samples/grpc-echo/README.md
istio-1.18.2/samples/grpc-echo/grpc-echo.yaml
istio-1.18.2/samples/health-check/
istio-1.18.2/samples/health-check/liveness-command.yaml
istio-1.18.2/samples/health-check/liveness-http-same-port.yaml
istio-1.18.2/samples/certs/
istio-1.18.2/samples/certs/workload-bar-key.pem
istio-1.18.2/samples/certs/ca-key.pem
istio-1.18.2/samples/certs/leaf-workload-bar-cert.pem
istio-1.18.2/samples/certs/workload-bar-root-certs.pem
istio-1.18.2/samples/certs/workload-foo-key.pem
istio-1.18.2/samples/certs/README.md
istio-1.18.2/samples/certs/ca-key-alt.pem
istio-1.18.2/samples/certs/root-cert-alt.pem
istio-1.18.2/samples/certs/cert-chain.pem
istio-1.18.2/samples/certs/cert-chain-alt.pem
istio-1.18.2/samples/certs/workload-foo-root-certs.pem
istio-1.18.2/samples/certs/root-cert.pem
istio-1.18.2/samples/certs/ca-cert-alt.pem
istio-1.18.2/samples/certs/ca-cert.pem
istio-1.18.2/samples/certs/workload-bar-cert.pem
istio-1.18.2/samples/certs/leaf-workload-foo-cert.pem
istio-1.18.2/samples/certs/workload-foo-cert.pem
istio-1.18.2/samples/certs/generate-workload.sh
istio-1.18.2/samples/sleep/
istio-1.18.2/samples/sleep/README.md
istio-1.18.2/samples/sleep/sleep-vault.yaml
istio-1.18.2/samples/sleep/sleep.yaml
istio-1.18.2/samples/sleep/notsleep.yaml
istio-1.18.2/tools/
istio-1.18.2/tools/_istioctl
istio-1.18.2/tools/certs/
istio-1.18.2/tools/certs/common.mk
istio-1.18.2/tools/certs/Makefile.selfsigned.mk
istio-1.18.2/tools/certs/README.md
istio-1.18.2/tools/certs/Makefile.k8s.mk
istio-1.18.2/tools/istioctl.bash
istio-1.18.2/LICENSE
istio-1.18.2/manifests/
istio-1.18.2/manifests/charts/
istio-1.18.2/manifests/charts/default/
istio-1.18.2/manifests/charts/default/templates/
istio-1.18.2/manifests/charts/default/templates/validatingwebhook.yaml
istio-1.18.2/manifests/charts/default/templates/mutatingwebhook.yaml
istio-1.18.2/manifests/charts/default/Chart.yaml
istio-1.18.2/manifests/charts/default/values.yaml
istio-1.18.2/manifests/charts/istiod-remote/
istio-1.18.2/manifests/charts/istiod-remote/NOTES.txt
istio-1.18.2/manifests/charts/istiod-remote/templates/
istio-1.18.2/manifests/charts/istiod-remote/templates/endpoints.yaml
istio-1.18.2/manifests/charts/istiod-remote/templates/default.yaml
istio-1.18.2/manifests/charts/istiod-remote/templates/_helpers.tpl
istio-1.18.2/manifests/charts/istiod-remote/templates/clusterrole.yaml
istio-1.18.2/manifests/charts/istiod-remote/templates/telemetryv2_1.18.yaml
istio-1.18.2/manifests/charts/istiod-remote/templates/reader-clusterrolebinding.yaml
istio-1.18.2/manifests/charts/istiod-remote/templates/clusterrolebinding.yaml
istio-1.18.2/manifests/charts/istiod-remote/templates/configmap.yaml
istio-1.18.2/manifests/charts/istiod-remote/templates/telemetryv2_1.17.yaml
istio-1.18.2/manifests/charts/istiod-remote/templates/services.yaml
istio-1.18.2/manifests/charts/istiod-remote/templates/role.yaml
istio-1.18.2/manifests/charts/istiod-remote/templates/reader-serviceaccount.yaml
istio-1.18.2/manifests/charts/istiod-remote/templates/reader-clusterrole.yaml
istio-1.18.2/manifests/charts/istiod-remote/templates/mutatingwebhook.yaml
istio-1.18.2/manifests/charts/istiod-remote/templates/serviceaccount.yaml
istio-1.18.2/manifests/charts/istiod-remote/templates/crd-all.gen.yaml
istio-1.18.2/manifests/charts/istiod-remote/templates/rolebinding.yaml
istio-1.18.2/manifests/charts/istiod-remote/templates/validatingwebhookconfiguration.yaml
istio-1.18.2/manifests/charts/istiod-remote/templates/istiod-injector-configmap.yaml
istio-1.18.2/manifests/charts/istiod-remote/templates/crd-operator.yaml
istio-1.18.2/manifests/charts/istiod-remote/templates/telemetryv2_1.16.yaml
istio-1.18.2/manifests/charts/istiod-remote/Chart.yaml
istio-1.18.2/manifests/charts/istiod-remote/values.yaml
istio-1.18.2/manifests/charts/istiod-remote/files/
istio-1.18.2/manifests/charts/istiod-remote/files/injection-template.yaml
istio-1.18.2/manifests/charts/istiod-remote/files/gateway-injection-template.yaml
istio-1.18.2/manifests/charts/istio-control/
istio-1.18.2/manifests/charts/istio-control/istio-discovery/
istio-1.18.2/manifests/charts/istio-control/istio-discovery/templates/
istio-1.18.2/manifests/charts/istio-control/istio-discovery/templates/revision-tags.yaml
istio-1.18.2/manifests/charts/istio-control/istio-discovery/templates/_helpers.tpl
istio-1.18.2/manifests/charts/istio-control/istio-discovery/templates/clusterrole.yaml
istio-1.18.2/manifests/charts/istio-control/istio-discovery/templates/telemetryv2_1.18.yaml
istio-1.18.2/manifests/charts/istio-control/istio-discovery/templates/NOTES.txt
istio-1.18.2/manifests/charts/istio-control/istio-discovery/templates/reader-clusterrolebinding.yaml
istio-1.18.2/manifests/charts/istio-control/istio-discovery/templates/service.yaml
istio-1.18.2/manifests/charts/istio-control/istio-discovery/templates/clusterrolebinding.yaml
istio-1.18.2/manifests/charts/istio-control/istio-discovery/templates/configmap.yaml
istio-1.18.2/manifests/charts/istio-control/istio-discovery/templates/telemetryv2_1.17.yaml
istio-1.18.2/manifests/charts/istio-control/istio-discovery/templates/role.yaml
istio-1.18.2/manifests/charts/istio-control/istio-discovery/templates/reader-clusterrole.yaml
istio-1.18.2/manifests/charts/istio-control/istio-discovery/templates/poddisruptionbudget.yaml
istio-1.18.2/manifests/charts/istio-control/istio-discovery/templates/configmap-jwks.yaml
istio-1.18.2/manifests/charts/istio-control/istio-discovery/templates/mutatingwebhook.yaml
istio-1.18.2/manifests/charts/istio-control/istio-discovery/templates/serviceaccount.yaml
istio-1.18.2/manifests/charts/istio-control/istio-discovery/templates/rolebinding.yaml
istio-1.18.2/manifests/charts/istio-control/istio-discovery/templates/validatingwebhookconfiguration.yaml
istio-1.18.2/manifests/charts/istio-control/istio-discovery/templates/istiod-injector-configmap.yaml
istio-1.18.2/manifests/charts/istio-control/istio-discovery/templates/autoscale.yaml
istio-1.18.2/manifests/charts/istio-control/istio-discovery/templates/deployment.yaml
istio-1.18.2/manifests/charts/istio-control/istio-discovery/templates/telemetryv2_1.16.yaml
istio-1.18.2/manifests/charts/istio-control/istio-discovery/README.md
istio-1.18.2/manifests/charts/istio-control/istio-discovery/Chart.yaml
istio-1.18.2/manifests/charts/istio-control/istio-discovery/values.yaml
istio-1.18.2/manifests/charts/istio-control/istio-discovery/files/
istio-1.18.2/manifests/charts/istio-control/istio-discovery/files/injection-template.yaml
istio-1.18.2/manifests/charts/istio-control/istio-discovery/files/kube-gateway.yaml
istio-1.18.2/manifests/charts/istio-control/istio-discovery/files/grpc-agent.yaml
istio-1.18.2/manifests/charts/istio-control/istio-discovery/files/waypoint.yaml
istio-1.18.2/manifests/charts/istio-control/istio-discovery/files/grpc-simple.yaml
istio-1.18.2/manifests/charts/istio-control/istio-discovery/files/gateway-injection-template.yaml
istio-1.18.2/manifests/charts/ztunnel/
istio-1.18.2/manifests/charts/ztunnel/templates/
istio-1.18.2/manifests/charts/ztunnel/templates/daemonset.yaml
istio-1.18.2/manifests/charts/ztunnel/templates/NOTES.txt
istio-1.18.2/manifests/charts/ztunnel/templates/rbac.yaml
istio-1.18.2/manifests/charts/ztunnel/README.md
istio-1.18.2/manifests/charts/ztunnel/Chart.yaml
istio-1.18.2/manifests/charts/ztunnel/values.yaml
istio-1.18.2/manifests/charts/istio-cni/
istio-1.18.2/manifests/charts/istio-cni/templates/
istio-1.18.2/manifests/charts/istio-cni/templates/daemonset.yaml
istio-1.18.2/manifests/charts/istio-cni/templates/clusterrole.yaml
istio-1.18.2/manifests/charts/istio-cni/templates/NOTES.txt
istio-1.18.2/manifests/charts/istio-cni/templates/clusterrolebinding.yaml
istio-1.18.2/manifests/charts/istio-cni/templates/resourcequota.yaml
istio-1.18.2/manifests/charts/istio-cni/templates/serviceaccount.yaml
istio-1.18.2/manifests/charts/istio-cni/templates/configmap-cni.yaml
istio-1.18.2/manifests/charts/istio-cni/README.md
istio-1.18.2/manifests/charts/istio-cni/Chart.yaml
istio-1.18.2/manifests/charts/istio-cni/values.yaml
istio-1.18.2/manifests/charts/README.md
istio-1.18.2/manifests/charts/gateway/
istio-1.18.2/manifests/charts/gateway/templates/
istio-1.18.2/manifests/charts/gateway/templates/hpa.yaml
istio-1.18.2/manifests/charts/gateway/templates/_helpers.tpl
istio-1.18.2/manifests/charts/gateway/templates/NOTES.txt
istio-1.18.2/manifests/charts/gateway/templates/service.yaml
istio-1.18.2/manifests/charts/gateway/templates/role.yaml
istio-1.18.2/manifests/charts/gateway/templates/poddisruptionbudget.yaml
istio-1.18.2/manifests/charts/gateway/templates/serviceaccount.yaml
istio-1.18.2/manifests/charts/gateway/templates/deployment.yaml
istio-1.18.2/manifests/charts/gateway/README.md
istio-1.18.2/manifests/charts/gateway/Chart.yaml
istio-1.18.2/manifests/charts/gateway/values.yaml
istio-1.18.2/manifests/charts/gateway/values.schema.json
istio-1.18.2/manifests/charts/base/
istio-1.18.2/manifests/charts/base/templates/
istio-1.18.2/manifests/charts/base/templates/endpoints.yaml
istio-1.18.2/manifests/charts/base/templates/default.yaml
istio-1.18.2/manifests/charts/base/templates/clusterrole.yaml
istio-1.18.2/manifests/charts/base/templates/crds.yaml
istio-1.18.2/manifests/charts/base/templates/NOTES.txt
istio-1.18.2/manifests/charts/base/templates/clusterrolebinding.yaml
istio-1.18.2/manifests/charts/base/templates/services.yaml
istio-1.18.2/manifests/charts/base/templates/role.yaml
istio-1.18.2/manifests/charts/base/templates/reader-serviceaccount.yaml
istio-1.18.2/manifests/charts/base/templates/serviceaccount.yaml
istio-1.18.2/manifests/charts/base/templates/rolebinding.yaml
istio-1.18.2/manifests/charts/base/README.md
istio-1.18.2/manifests/charts/base/Chart.yaml
istio-1.18.2/manifests/charts/base/values.yaml
istio-1.18.2/manifests/charts/base/crds/
istio-1.18.2/manifests/charts/base/crds/crd-all.gen.yaml
istio-1.18.2/manifests/charts/base/crds/crd-operator.yaml
istio-1.18.2/manifests/charts/install-OpenShift.md
istio-1.18.2/manifests/charts/UPDATING-CHARTS.md
istio-1.18.2/manifests/charts/gateways/
istio-1.18.2/manifests/charts/gateways/istio-egress/
istio-1.18.2/manifests/charts/gateways/istio-egress/NOTES.txt
istio-1.18.2/manifests/charts/gateways/istio-egress/templates/
istio-1.18.2/manifests/charts/gateways/istio-egress/templates/rolebindings.yaml
istio-1.18.2/manifests/charts/gateways/istio-egress/templates/injected-deployment.yaml
istio-1.18.2/manifests/charts/gateways/istio-egress/templates/service.yaml
istio-1.18.2/manifests/charts/gateways/istio-egress/templates/_affinity.tpl
istio-1.18.2/manifests/charts/gateways/istio-egress/templates/role.yaml
istio-1.18.2/manifests/charts/gateways/istio-egress/templates/poddisruptionbudget.yaml
istio-1.18.2/manifests/charts/gateways/istio-egress/templates/serviceaccount.yaml
istio-1.18.2/manifests/charts/gateways/istio-egress/templates/autoscale.yaml
istio-1.18.2/manifests/charts/gateways/istio-egress/templates/deployment.yaml
istio-1.18.2/manifests/charts/gateways/istio-egress/Chart.yaml
istio-1.18.2/manifests/charts/gateways/istio-egress/values.yaml
istio-1.18.2/manifests/charts/gateways/istio-ingress/
istio-1.18.2/manifests/charts/gateways/istio-ingress/NOTES.txt
istio-1.18.2/manifests/charts/gateways/istio-ingress/templates/
istio-1.18.2/manifests/charts/gateways/istio-ingress/templates/rolebindings.yaml
istio-1.18.2/manifests/charts/gateways/istio-ingress/templates/injected-deployment.yaml
istio-1.18.2/manifests/charts/gateways/istio-ingress/templates/service.yaml
istio-1.18.2/manifests/charts/gateways/istio-ingress/templates/_affinity.tpl
istio-1.18.2/manifests/charts/gateways/istio-ingress/templates/role.yaml
istio-1.18.2/manifests/charts/gateways/istio-ingress/templates/poddisruptionbudget.yaml
istio-1.18.2/manifests/charts/gateways/istio-ingress/templates/serviceaccount.yaml
istio-1.18.2/manifests/charts/gateways/istio-ingress/templates/autoscale.yaml
istio-1.18.2/manifests/charts/gateways/istio-ingress/templates/deployment.yaml
istio-1.18.2/manifests/charts/gateways/istio-ingress/Chart.yaml
istio-1.18.2/manifests/charts/gateways/istio-ingress/values.yaml
istio-1.18.2/manifests/charts/istio-operator/
istio-1.18.2/manifests/charts/istio-operator/templates/
istio-1.18.2/manifests/charts/istio-operator/templates/clusterrole.yaml
istio-1.18.2/manifests/charts/istio-operator/templates/crds.yaml
istio-1.18.2/manifests/charts/istio-operator/templates/service_account.yaml
istio-1.18.2/manifests/charts/istio-operator/templates/service.yaml
istio-1.18.2/manifests/charts/istio-operator/templates/clusterrole_binding.yaml
istio-1.18.2/manifests/charts/istio-operator/templates/deployment.yaml
istio-1.18.2/manifests/charts/istio-operator/Chart.yaml
istio-1.18.2/manifests/charts/istio-operator/values.yaml
istio-1.18.2/manifests/charts/istio-operator/crds/
istio-1.18.2/manifests/charts/istio-operator/crds/crd-operator.yaml
istio-1.18.2/manifests/examples/
istio-1.18.2/manifests/examples/customresource/
istio-1.18.2/manifests/examples/customresource/istio_v1alpha1_istiooperator_cr.yaml
istio-1.18.2/manifests/examples/user-gateway/
istio-1.18.2/manifests/examples/user-gateway/ingress-gateway-only.yaml
istio-1.18.2/manifests/profiles/
istio-1.18.2/manifests/profiles/default.yaml
istio-1.18.2/manifests/profiles/demo.yaml
istio-1.18.2/manifests/profiles/remote.yaml
istio-1.18.2/manifests/profiles/external.yaml
istio-1.18.2/manifests/profiles/empty.yaml
istio-1.18.2/manifests/profiles/ambient.yaml
istio-1.18.2/manifests/profiles/preview.yaml
istio-1.18.2/manifests/profiles/minimal.yaml
istio-1.18.2/manifests/profiles/openshift.yaml
istio-1.18.2/bin/
istio-1.18.2/bin/istioctl
Infer repository core for addon dns
Addon core/dns is already enabled
✔ Istio core installed
✔ Istiod installed
✔ Egress gateways installed
✔ Ingress gateways installed
✔ Installation completeMaking this installation the default for injection and validation.
Istio is starting

To configure mutual TLS authentication consult the Istio documentation.

$ microk8s status --wait-ready

microk8s is running
high-availability: no
  datastore master nodes: 127.0.0.1:19001
  datastore standby nodes: none
addons:
  enabled:
    istio                # (community) Core Istio service mesh services
    community            # (core) The community addons repository
    dns                  # (core) CoreDNS
    ha-cluster           # (core) Configure high availability on the current node
    helm                 # (core) Helm - the package manager for Kubernetes
    helm3                # (core) Helm 3 - the package manager for Kubernetes
    hostpath-storage     # (core) Storage class; allocates storage from host directory
    metallb              # (core) Loadbalancer for your Kubernetes cluster
    storage              # (core) Alias to hostpath-storage add-on, deprecated
  disabled:
    argocd               # (community) Argo CD is a declarative continuous deployment for Kubernetes.
    cilium               # (community) SDN, fast with full network policy
    cloudnative-pg       # (community) PostgreSQL operator CloudNativePG
    dashboard-ingress    # (community) Ingress definition for Kubernetes dashboard
    easyhaproxy          # (community) EasyHAProxy can configure HAProxy automatically based on ingress labels
    falco                # (community) Cloud-native runtime threat detection tool for Linux and K8s
    fluentd              # (community) Elasticsearch-Fluentd-Kibana logging and monitoring
    gopaddle             # (community) DevSecOps and Multi-Cloud Kubernetes Platform
    inaccel              # (community) Simplifying FPGA management in Kubernetes
    jaeger               # (community) Kubernetes Jaeger operator with its simple config
    kata                 # (community) Kata Containers is a secure runtime with lightweight VMS
    keda                 # (community) Kubernetes-based Event Driven Autoscaling
    knative              # (community) Knative Serverless and Event Driven Applications
    kubearmor            # (community) Cloud-native runtime security enforcement system for k8s
    kwasm                # (community) WebAssembly support for WasmEdge (Docker Wasm) and Spin (Azure AKS WASI)
    linkerd              # (community) Linkerd is a service mesh for Kubernetes and other frameworks
    microcks             # (community) Open source Kubernetes Native tool for API Mocking and Testing
    multus               # (community) Multus CNI enables attaching multiple network interfaces to pods
    nfs                  # (community) NFS Server Provisioner
    ngrok                # (community) ngrok Ingress Controller instantly adds connectivity, load balancing, authentication, and observability to your services
    openebs              # (community) OpenEBS is the open-source storage solution for Kubernetes
    openfaas             # (community) OpenFaaS serverless framework
    osm-edge             # (community) osm-edge is a lightweight SMI compatible service mesh for the edge-computing.
    parking              # (community) Static webserver to park a domain. Works with EasyHAProxy.
    portainer            # (community) Portainer UI for your Kubernetes cluster
    shifu                # (community) Kubernetes native IoT software development framework.
    sosivio              # (community) Kubernetes Predictive Troubleshooting, Observability, and Resource Optimization
    stunner              # (community) A Kubernetes media gateway for WebRTC
    traefik              # (community) traefik Ingress controller
    trivy                # (community) Kubernetes-native security scanner
    cert-manager         # (core) Cloud native certificate management
    cis-hardening        # (core) Apply CIS K8s hardening
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


$ microk8s kubectl get all --all-namespaces

NAMESPACE        NAME                                                  READY   STATUS    RESTARTS      AGE
default          pod/keycloak-23-0                                     1/1     Running   0             17d
default          pod/keycloak-23-postgresql-0                          1/1     Running   0             17d
default          pod/postgres-7f8c6b8ddf-9j6tx                         1/1     Running   0             18d
default          pod/sample-kubernetes-dbaccess-app-6f6dc66cb7-glbwj   1/1     Running   0             17d
default          pod/service-mesh-app-service3-6bc878669c-s6kfd        1/1     Running   0             2d16h
istio-system     pod/istio-egressgateway-d67468644-tjsnq               1/1     Running   0             10m
istio-system     pod/istio-ingressgateway-846c886d68-xtvdp             1/1     Running   0             10m
istio-system     pod/istiod-6df64b8546-hhw7x                           1/1     Running   0             10m
kafka            pod/sample-cluster-entity-operator-dbdbbcb78-46ss8    2/2     Running   0             8d
kafka            pod/sample-cluster-kafka-0                            1/1     Running   0             8d
kafka            pod/sample-cluster-zookeeper-0                        1/1     Running   0             8d
kafka            pod/strimzi-cluster-operator-6948497896-4wfdj         1/1     Running   0             15d
kube-system      pod/calico-kube-controllers-796fb75cc-lh9kb           1/1     Running   0             21d
kube-system      pod/calico-node-zxvfd                                 1/1     Running   0             16d
kube-system      pod/coredns-5986966c54-2fm58                          1/1     Running   0             21d
kube-system      pod/hostpath-provisioner-7c8bdf94b8-s4b45             1/1     Running   1 (16d ago)   18d
metallb-system   pod/controller-5484c5f99f-xlcsf                       1/1     Running   0             18d
metallb-system   pod/speaker-2vpvq                                     1/1     Running   0             18d

NAMESPACE        NAME                                              TYPE           CLUSTER-IP       EXTERNAL-IP   PORT(S)                                                                      AGE
default          service/keycloak-23                               ClusterIP      10.152.183.67    <none>        80/TCP                                                                       17d
default          service/keycloak-23-headless                      ClusterIP      None             <none>        8080/TCP                                                                     17d
default          service/keycloak-23-postgresql                    ClusterIP      10.152.183.227   <none>        5432/TCP                                                                     17d
default          service/keycloak-23-postgresql-hl                 ClusterIP      None             <none>        5432/TCP                                                                     17d
default          service/kubernetes                                ClusterIP      10.152.183.1     <none>        443/TCP                                                                      21d
default          service/postgres                                  NodePort       10.152.183.56    <none>        5432:30432/TCP                                                               18d
default          service/sample-kubernetes-dbaccess-app            ClusterIP      10.152.183.180   <none>        8080/TCP                                                                     17d
default          service/sample-kubernetes-dbaccess-app-lb         LoadBalancer   10.152.183.196   192.168.1.0   8080:31570/TCP                                                               17d
default          service/service-mesh-app-service3                 ClusterIP      10.152.183.51    <none>        8080/TCP                                                                     2d16h
istio-system     service/istio-egressgateway                       ClusterIP      10.152.183.190   <none>        80/TCP,443/TCP                                                               10m
istio-system     service/istio-ingressgateway                      LoadBalancer   10.152.183.224   192.168.1.3   15021:32431/TCP,80:30734/TCP,443:31270/TCP,31400:30418/TCP,15443:30314/TCP   10m
istio-system     service/istiod                                    ClusterIP      10.152.183.87    <none>        15010/TCP,15012/TCP,443/TCP,15014/TCP                                        10m
kafka            service/sample-cluster-kafka-0                    LoadBalancer   10.152.183.95    192.168.1.2   9094:31141/TCP                                                               8d
kafka            service/sample-cluster-kafka-bootstrap            ClusterIP      10.152.183.165   <none>        9091/TCP,9092/TCP,9093/TCP                                                   8d
kafka            service/sample-cluster-kafka-brokers              ClusterIP      None             <none>        9090/TCP,9091/TCP,8443/TCP,9092/TCP,9093/TCP                                 8d
kafka            service/sample-cluster-kafka-external-bootstrap   LoadBalancer   10.152.183.70    192.168.1.1   9094:30113/TCP                                                               8d
kafka            service/sample-cluster-zookeeper-client           ClusterIP      10.152.183.45    <none>        2181/TCP                                                                     8d
kafka            service/sample-cluster-zookeeper-nodes            ClusterIP      None             <none>        2181/TCP,2888/TCP,3888/TCP                                                   8d
kube-system      service/kube-dns                                  ClusterIP      10.152.183.10    <none>        53/UDP,53/TCP,9153/TCP                                                       21d
metallb-system   service/webhook-service                           ClusterIP      10.152.183.57    <none>        443/TCP                                                                      18d

NAMESPACE        NAME                         DESIRED   CURRENT   READY   UP-TO-DATE   AVAILABLE   NODE SELECTOR            AGE
kube-system      daemonset.apps/calico-node   1         1         1       1            1           kubernetes.io/os=linux   21d
metallb-system   daemonset.apps/speaker       1         1         1       1            1           kubernetes.io/os=linux   18d

NAMESPACE        NAME                                             READY   UP-TO-DATE   AVAILABLE   AGE
default          deployment.apps/postgres                         1/1     1            1           18d
default          deployment.apps/sample-kubernetes-dbaccess-app   1/1     1            1           17d
default          deployment.apps/service-mesh-app-service3        1/1     1            1           2d16h
istio-system     deployment.apps/istio-egressgateway              1/1     1            1           10m
istio-system     deployment.apps/istio-ingressgateway             1/1     1            1           10m
istio-system     deployment.apps/istiod                           1/1     1            1           10m
kafka            deployment.apps/sample-cluster-entity-operator   1/1     1            1           8d
kafka            deployment.apps/strimzi-cluster-operator         1/1     1            1           15d
kube-system      deployment.apps/calico-kube-controllers          1/1     1            1           21d
kube-system      deployment.apps/coredns                          1/1     1            1           21d
kube-system      deployment.apps/hostpath-provisioner             1/1     1            1           18d
metallb-system   deployment.apps/controller                       1/1     1            1           18d

NAMESPACE        NAME                                                        DESIRED   CURRENT   READY   AGE
default          replicaset.apps/postgres-7f8c6b8ddf                         1         1         1       18d
default          replicaset.apps/sample-kubernetes-dbaccess-app-6f6dc66cb7   1         1         1       17d
default          replicaset.apps/service-mesh-app-service3-6bc878669c        1         1         1       2d16h
istio-system     replicaset.apps/istio-egressgateway-d67468644               1         1         1       10m
istio-system     replicaset.apps/istio-ingressgateway-846c886d68             1         1         1       10m
istio-system     replicaset.apps/istiod-6df64b8546                           1         1         1       10m
kafka            replicaset.apps/sample-cluster-entity-operator-dbdbbcb78    1         1         1       8d
kafka            replicaset.apps/strimzi-cluster-operator-6948497896         1         1         1       15d
kube-system      replicaset.apps/calico-kube-controllers-796fb75cc           1         1         1       21d
kube-system      replicaset.apps/coredns-5986966c54                          1         1         1       21d
kube-system      replicaset.apps/hostpath-provisioner-7c8bdf94b8             1         1         1       18d
metallb-system   replicaset.apps/controller-5484c5f99f                       1         1         1       18d

NAMESPACE   NAME                                      READY   AGE
default     statefulset.apps/keycloak-23              1/1     17d
default     statefulset.apps/keycloak-23-postgresql   1/1     17d

$ microk8s disable istio
Infer repository community for addon istio
Disabling Istio
(uninstall has graduated. Use `istioctl uninstall`)
All Istio resources will be pruned from the cluster

  Removed IstioOperator:istio-system:installed-state.
  Removed Deployment:istio-system:istio-egressgateway.
  Removed Deployment:istio-system:istio-ingressgateway.
  Removed Deployment:istio-system:istiod.
  Removed Service:istio-system:istio-egressgateway.
  Removed Service:istio-system:istio-ingressgateway.
  Removed Service:istio-system:istiod.
  Removed ConfigMap:istio-system:istio.
  Removed ConfigMap:istio-system:istio-sidecar-injector.
  Removed Pod:istio-system:istio-egressgateway-d67468644-tjsnq.
  Removed Pod:istio-system:istio-ingressgateway-846c886d68-xtvdp.
  Removed Pod:istio-system:istiod-6df64b8546-hhw7x.
  Removed ServiceAccount:istio-system:istio-egressgateway-service-account.
  Removed ServiceAccount:istio-system:istio-ingressgateway-service-account.
  Removed ServiceAccount:istio-system:istio-reader-service-account.
  Removed ServiceAccount:istio-system:istiod.
  Removed ServiceAccount:istio-system:istiod-service-account.
  Removed RoleBinding:istio-system:istio-egressgateway-sds.
  Removed RoleBinding:istio-system:istio-ingressgateway-sds.
  Removed RoleBinding:istio-system:istiod.
  Removed RoleBinding:istio-system:istiod-istio-system.
  Removed Role:istio-system:istio-egressgateway-sds.
  Removed Role:istio-system:istio-ingressgateway-sds.
  Removed Role:istio-system:istiod.
  Removed Role:istio-system:istiod-istio-system.
  Removed PodDisruptionBudget:istio-system:istio-egressgateway.
  Removed PodDisruptionBudget:istio-system:istio-ingressgateway.
  Removed PodDisruptionBudget:istio-system:istiod.
  Removed MutatingWebhookConfiguration::istio-revision-tag-default.
  Removed MutatingWebhookConfiguration::istio-sidecar-injector.
  Removed ValidatingWebhookConfiguration::istio-validator-istio-system.
  Removed ValidatingWebhookConfiguration::istiod-default-validator.
  Removed ClusterRole::istio-reader-clusterrole-istio-system.
  Removed ClusterRole::istio-reader-istio-system.
  Removed ClusterRole::istiod-clusterrole-istio-system.
  Removed ClusterRole::istiod-gateway-controller-istio-system.
  Removed ClusterRole::istiod-istio-system.
  Removed ClusterRoleBinding::istio-reader-clusterrole-istio-system.
  Removed ClusterRoleBinding::istio-reader-istio-system.
  Removed ClusterRoleBinding::istiod-clusterrole-istio-system.
  Removed ClusterRoleBinding::istiod-gateway-controller-istio-system.
  Removed ClusterRoleBinding::istiod-istio-system.
  Removed CustomResourceDefinition::authorizationpolicies.security.istio.io.
  Removed CustomResourceDefinition::destinationrules.networking.istio.io.
  Removed CustomResourceDefinition::envoyfilters.networking.istio.io.
  Removed CustomResourceDefinition::gateways.networking.istio.io.
  Removed CustomResourceDefinition::istiooperators.install.istio.io.
  Removed CustomResourceDefinition::peerauthentications.security.istio.io.
  Removed CustomResourceDefinition::proxyconfigs.networking.istio.io.
  Removed CustomResourceDefinition::requestauthentications.security.istio.io.
  Removed CustomResourceDefinition::serviceentries.networking.istio.io.
  Removed CustomResourceDefinition::sidecars.networking.istio.io.
  Removed CustomResourceDefinition::telemetries.telemetry.istio.io.
  Removed CustomResourceDefinition::virtualservices.networking.istio.io.
  Removed CustomResourceDefinition::wasmplugins.extensions.istio.io.
  Removed CustomResourceDefinition::workloadentries.networking.istio.io.
  Removed CustomResourceDefinition::workloadgroups.networking.istio.io.
✔ Uninstall completeIstio is terminating

```



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

----
[index]

1. [Set up RHEL EC2 instance on AWS](1-set-up-rhel-instance-on-aws.md)
2. [Set up MicroK8s](2-set-up-microk8s.md)
3. [Set up Application Environment](3-set-up-app-env.md)
4. [Implementation of Application](4-implementation-app.md)
5. [Deploy Application](5-deploy-app.md)
6. [Implementation of Service Mesh Application](6-implementation-service-mesh-app.md)
7. [Deploy Service Mesh Application](7-deploy-service-mesh-app.md)
