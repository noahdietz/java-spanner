/*
 * Copyright 2018 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.cloud.spanner.spi.v1;

import static com.google.cloud.spanner.SpannerExceptionFactory.newSpannerException;

import com.google.api.core.ApiFuture;
import com.google.api.core.InternalApi;
import com.google.api.core.NanoClock;
import com.google.api.gax.core.CredentialsProvider;
import com.google.api.gax.core.ExecutorProvider;
import com.google.api.gax.core.GaxProperties;
import com.google.api.gax.grpc.GaxGrpcProperties;
import com.google.api.gax.grpc.GrpcCallContext;
import com.google.api.gax.grpc.InstantiatingGrpcChannelProvider;
import com.google.api.gax.longrunning.OperationFuture;
import com.google.api.gax.retrying.ResultRetryAlgorithm;
import com.google.api.gax.retrying.RetrySettings;
import com.google.api.gax.retrying.TimedAttemptSettings;
import com.google.api.gax.rpc.AlreadyExistsException;
import com.google.api.gax.rpc.ApiClientHeaderProvider;
import com.google.api.gax.rpc.ApiException;
import com.google.api.gax.rpc.HeaderProvider;
import com.google.api.gax.rpc.InstantiatingWatchdogProvider;
import com.google.api.gax.rpc.OperationCallable;
import com.google.api.gax.rpc.ResponseObserver;
import com.google.api.gax.rpc.ServerStream;
import com.google.api.gax.rpc.StatusCode;
import com.google.api.gax.rpc.StreamController;
import com.google.api.gax.rpc.TransportChannelProvider;
import com.google.api.gax.rpc.WatchdogProvider;
import com.google.api.pathtemplate.PathTemplate;
import com.google.cloud.RetryHelper;
import com.google.cloud.grpc.GrpcTransportOptions;
import com.google.cloud.spanner.SpannerException;
import com.google.cloud.spanner.SpannerExceptionFactory;
import com.google.cloud.spanner.SpannerOptions;
import com.google.cloud.spanner.SpannerOptions.CallCredentialsProvider;
import com.google.cloud.spanner.admin.database.v1.stub.DatabaseAdminStub;
import com.google.cloud.spanner.admin.database.v1.stub.DatabaseAdminStubSettings;
import com.google.cloud.spanner.admin.database.v1.stub.GrpcDatabaseAdminStub;
import com.google.cloud.spanner.admin.instance.v1.stub.GrpcInstanceAdminStub;
import com.google.cloud.spanner.admin.instance.v1.stub.InstanceAdminStub;
import com.google.cloud.spanner.spi.v1.SpannerRpc.Option;
import com.google.cloud.spanner.v1.stub.GrpcSpannerStub;
import com.google.cloud.spanner.v1.stub.SpannerStub;
import com.google.cloud.spanner.v1.stub.SpannerStubSettings;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Function;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.RateLimiter;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.iam.v1.GetIamPolicyRequest;
import com.google.iam.v1.Policy;
import com.google.iam.v1.SetIamPolicyRequest;
import com.google.iam.v1.TestIamPermissionsRequest;
import com.google.iam.v1.TestIamPermissionsResponse;
import com.google.longrunning.CancelOperationRequest;
import com.google.longrunning.GetOperationRequest;
import com.google.longrunning.Operation;
import com.google.protobuf.Empty;
import com.google.protobuf.FieldMask;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import com.google.protobuf.Timestamp;
import com.google.spanner.admin.database.v1.Backup;
import com.google.spanner.admin.database.v1.CreateBackupMetadata;
import com.google.spanner.admin.database.v1.CreateBackupRequest;
import com.google.spanner.admin.database.v1.CreateDatabaseMetadata;
import com.google.spanner.admin.database.v1.CreateDatabaseRequest;
import com.google.spanner.admin.database.v1.Database;
import com.google.spanner.admin.database.v1.DeleteBackupRequest;
import com.google.spanner.admin.database.v1.DropDatabaseRequest;
import com.google.spanner.admin.database.v1.GetBackupRequest;
import com.google.spanner.admin.database.v1.GetDatabaseDdlRequest;
import com.google.spanner.admin.database.v1.GetDatabaseRequest;
import com.google.spanner.admin.database.v1.ListBackupOperationsRequest;
import com.google.spanner.admin.database.v1.ListBackupOperationsResponse;
import com.google.spanner.admin.database.v1.ListBackupsRequest;
import com.google.spanner.admin.database.v1.ListBackupsResponse;
import com.google.spanner.admin.database.v1.ListDatabaseOperationsRequest;
import com.google.spanner.admin.database.v1.ListDatabaseOperationsResponse;
import com.google.spanner.admin.database.v1.ListDatabasesRequest;
import com.google.spanner.admin.database.v1.ListDatabasesResponse;
import com.google.spanner.admin.database.v1.RestoreDatabaseMetadata;
import com.google.spanner.admin.database.v1.RestoreDatabaseRequest;
import com.google.spanner.admin.database.v1.UpdateBackupRequest;
import com.google.spanner.admin.database.v1.UpdateDatabaseDdlMetadata;
import com.google.spanner.admin.database.v1.UpdateDatabaseDdlRequest;
import com.google.spanner.admin.instance.v1.CreateInstanceMetadata;
import com.google.spanner.admin.instance.v1.CreateInstanceRequest;
import com.google.spanner.admin.instance.v1.DeleteInstanceRequest;
import com.google.spanner.admin.instance.v1.GetInstanceConfigRequest;
import com.google.spanner.admin.instance.v1.GetInstanceRequest;
import com.google.spanner.admin.instance.v1.Instance;
import com.google.spanner.admin.instance.v1.InstanceConfig;
import com.google.spanner.admin.instance.v1.ListInstanceConfigsRequest;
import com.google.spanner.admin.instance.v1.ListInstanceConfigsResponse;
import com.google.spanner.admin.instance.v1.ListInstancesRequest;
import com.google.spanner.admin.instance.v1.ListInstancesResponse;
import com.google.spanner.admin.instance.v1.UpdateInstanceMetadata;
import com.google.spanner.admin.instance.v1.UpdateInstanceRequest;
import com.google.spanner.v1.BatchCreateSessionsRequest;
import com.google.spanner.v1.BeginTransactionRequest;
import com.google.spanner.v1.CommitRequest;
import com.google.spanner.v1.CommitResponse;
import com.google.spanner.v1.CreateSessionRequest;
import com.google.spanner.v1.DeleteSessionRequest;
import com.google.spanner.v1.ExecuteBatchDmlRequest;
import com.google.spanner.v1.ExecuteBatchDmlResponse;
import com.google.spanner.v1.ExecuteSqlRequest;
import com.google.spanner.v1.PartialResultSet;
import com.google.spanner.v1.PartitionQueryRequest;
import com.google.spanner.v1.PartitionReadRequest;
import com.google.spanner.v1.PartitionResponse;
import com.google.spanner.v1.ReadRequest;
import com.google.spanner.v1.ResultSet;
import com.google.spanner.v1.RollbackRequest;
import com.google.spanner.v1.Session;
import com.google.spanner.v1.Transaction;
import io.grpc.CallCredentials;
import io.grpc.Context;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;
import org.threeten.bp.Duration;

/** Implementation of Cloud Spanner remote calls using Gapic libraries. */
@InternalApi
public class GapicSpannerRpc implements SpannerRpc {
  /**
   * {@link ExecutorProvider} that keeps track of the executors that are created and shuts these
   * down when the {@link SpannerRpc} is closed.
   */
  private static final class ManagedInstantiatingExecutorProvider implements ExecutorProvider {
    // 4 Gapic clients * 4 channels per client.
    private static final int DEFAULT_MIN_THREAD_COUNT = 16;
    private final List<ScheduledExecutorService> executors = new LinkedList<>();
    private final ThreadFactory threadFactory;

    private ManagedInstantiatingExecutorProvider(ThreadFactory threadFactory) {
      this.threadFactory = threadFactory;
    }

    @Override
    public boolean shouldAutoClose() {
      return false;
    }

    @Override
    public ScheduledExecutorService getExecutor() {
      int numCpus = Runtime.getRuntime().availableProcessors();
      int numThreads = Math.max(DEFAULT_MIN_THREAD_COUNT, numCpus);
      ScheduledExecutorService executor =
          new ScheduledThreadPoolExecutor(numThreads, threadFactory);
      synchronized (this) {
        executors.add(executor);
      }
      return executor;
    }

    /** Shuts down all executors that have been created by this {@link ExecutorProvider}. */
    private synchronized void shutdown() {
      for (ScheduledExecutorService executor : executors) {
        executor.shutdown();
      }
    }

    private void awaitTermination() throws InterruptedException {
      for (ScheduledExecutorService executor : executors) {
        executor.awaitTermination(10L, TimeUnit.SECONDS);
      }
    }
  }

  private static final PathTemplate PROJECT_NAME_TEMPLATE =
      PathTemplate.create("projects/{project}");
  private static final PathTemplate OPERATION_NAME_TEMPLATE =
      PathTemplate.create("{database=projects/*/instances/*/databases/*}/operations/{operation}");
  private static final int MAX_MESSAGE_SIZE = 100 * 1024 * 1024;
  private static final int MAX_METADATA_SIZE = 32 * 1024; // bytes
  private static final String PROPERTY_TIMEOUT_SECONDS =
      "com.google.cloud.spanner.watchdogTimeoutSeconds";
  private static final String PROPERTY_PERIOD_SECONDS =
      "com.google.cloud.spanner.watchdogPeriodSeconds";
  private static final int DEFAULT_TIMEOUT_SECONDS = 30 * 60;
  private static final int DEFAULT_PERIOD_SECONDS = 10;
  private static final int GRPC_KEEPALIVE_SECONDS = 2 * 60;

  private final ManagedInstantiatingExecutorProvider executorProvider;
  private boolean rpcIsClosed;
  private final SpannerStub spannerStub;
  private final SpannerStub partitionedDmlStub;
  private final RetrySettings partitionedDmlRetrySettings;
  private final InstanceAdminStub instanceAdminStub;
  private final DatabaseAdminStubSettings databaseAdminStubSettings;
  private final DatabaseAdminStub databaseAdminStub;
  private final String projectId;
  private final String projectName;
  private final SpannerMetadataProvider metadataProvider;
  private final CallCredentialsProvider callCredentialsProvider;
  private final String compressorName;
  private final Duration waitTimeout =
      systemProperty(PROPERTY_TIMEOUT_SECONDS, DEFAULT_TIMEOUT_SECONDS);
  private final Duration idleTimeout =
      systemProperty(PROPERTY_TIMEOUT_SECONDS, DEFAULT_TIMEOUT_SECONDS);
  private final Duration checkInterval =
      systemProperty(PROPERTY_PERIOD_SECONDS, DEFAULT_PERIOD_SECONDS);

  private final ScheduledExecutorService spannerWatchdog;

  private final boolean throttleAdministrativeRequests;
  private static final double ADMINISTRATIVE_REQUESTS_RATE_LIMIT = 1.0D;
  private static final ConcurrentMap<String, RateLimiter> ADMINISTRATIVE_REQUESTS_RATE_LIMITERS =
      new ConcurrentHashMap<String, RateLimiter>();

  public static GapicSpannerRpc create(SpannerOptions options) {
    return new GapicSpannerRpc(options);
  }

  public GapicSpannerRpc(final SpannerOptions options) {
    this.projectId = options.getProjectId();
    String projectNameStr = PROJECT_NAME_TEMPLATE.instantiate("project", this.projectId);
    try {
      // Fix use cases where projectName contains special charecters.
      // This would happen when projects are under an organization.
      projectNameStr = URLDecoder.decode(projectNameStr, StandardCharsets.UTF_8.toString());
    } catch (UnsupportedEncodingException e) { // Ignored.
    }
    this.projectName = projectNameStr;
    this.throttleAdministrativeRequests = options.isAutoThrottleAdministrativeRequests();
    if (throttleAdministrativeRequests) {
      ADMINISTRATIVE_REQUESTS_RATE_LIMITERS.putIfAbsent(
          projectNameStr, RateLimiter.create(ADMINISTRATIVE_REQUESTS_RATE_LIMIT));
    }

    // create a metadataProvider which combines both internal headers and
    // per-method-call extra headers for channelProvider to inject the headers
    // for rpc calls
    ApiClientHeaderProvider.Builder internalHeaderProviderBuilder =
        ApiClientHeaderProvider.newBuilder();
    ApiClientHeaderProvider internalHeaderProvider =
        internalHeaderProviderBuilder
            .setClientLibToken(
                options.getClientLibToken(), GaxProperties.getLibraryVersion(options.getClass()))
            .setTransportToken(
                GaxGrpcProperties.getGrpcTokenName(), GaxGrpcProperties.getGrpcVersion())
            .build();

    HeaderProvider mergedHeaderProvider = options.getMergedHeaderProvider(internalHeaderProvider);
    this.metadataProvider =
        SpannerMetadataProvider.create(
            mergedHeaderProvider.getHeaders(),
            internalHeaderProviderBuilder.getResourceHeaderKey());
    this.callCredentialsProvider = options.getCallCredentialsProvider();
    this.compressorName = options.getCompressorName();

    // Create a managed executor provider.
    this.executorProvider =
        new ManagedInstantiatingExecutorProvider(
            new ThreadFactoryBuilder()
                .setDaemon(true)
                .setNameFormat("Cloud-Spanner-TransportChannel-%d")
                .build());
    // First check if SpannerOptions provides a TransportChannerProvider. Create one
    // with information gathered from SpannerOptions if none is provided
    TransportChannelProvider channelProvider =
        MoreObjects.firstNonNull(
            options.getChannelProvider(),
            InstantiatingGrpcChannelProvider.newBuilder()
                .setChannelConfigurator(options.getChannelConfigurator())
                .setEndpoint(options.getEndpoint())
                .setMaxInboundMessageSize(MAX_MESSAGE_SIZE)
                .setMaxInboundMetadataSize(MAX_METADATA_SIZE)
                .setPoolSize(options.getNumChannels())
                .setExecutor(executorProvider.getExecutor())

                // Set a keepalive time of 120 seconds to help long running
                // commit GRPC calls succeed
                .setKeepAliveTime(Duration.ofSeconds(GRPC_KEEPALIVE_SECONDS * 1000))

                // Then check if SpannerOptions provides an InterceptorProvider. Create a default
                // SpannerInterceptorProvider if none is provided
                .setInterceptorProvider(
                    SpannerInterceptorProvider.create(
                            MoreObjects.firstNonNull(
                                options.getInterceptorProvider(),
                                SpannerInterceptorProvider.createDefault()))
                        .withEncoding(compressorName))
                .setHeaderProvider(mergedHeaderProvider)
                .build());

    CredentialsProvider credentialsProvider =
        GrpcTransportOptions.setUpCredentialsProvider(options);

    spannerWatchdog =
        Executors.newSingleThreadScheduledExecutor(
            new ThreadFactoryBuilder()
                .setDaemon(true)
                .setNameFormat("Cloud-Spanner-WatchdogProvider-%d")
                .build());
    WatchdogProvider watchdogProvider =
        InstantiatingWatchdogProvider.create()
            .withExecutor(spannerWatchdog)
            .withCheckInterval(checkInterval)
            .withClock(NanoClock.getDefaultClock());

    try {
      this.spannerStub =
          GrpcSpannerStub.create(
              options
                  .getSpannerStubSettings()
                  .toBuilder()
                  .setTransportChannelProvider(channelProvider)
                  .setCredentialsProvider(credentialsProvider)
                  .setStreamWatchdogProvider(watchdogProvider)
                  .build());
      partitionedDmlRetrySettings =
          options
              .getSpannerStubSettings()
              .executeSqlSettings()
              .getRetrySettings()
              .toBuilder()
              .setInitialRpcTimeout(options.getPartitionedDmlTimeout())
              .setMaxRpcTimeout(options.getPartitionedDmlTimeout())
              .setTotalTimeout(options.getPartitionedDmlTimeout())
              .setRpcTimeoutMultiplier(1.0)
              .build();
      SpannerStubSettings.Builder pdmlSettings = options.getSpannerStubSettings().toBuilder();
      pdmlSettings
          .setTransportChannelProvider(channelProvider)
          .setCredentialsProvider(credentialsProvider)
          .setStreamWatchdogProvider(watchdogProvider)
          .executeSqlSettings()
          .setRetrySettings(partitionedDmlRetrySettings);
      // The stream watchdog will by default only check for a timeout every 10 seconds, so if the
      // timeout is less than 10 seconds, it would be ignored for the first 10 seconds unless we
      // also change the StreamWatchdogCheckInterval.
      if (options
              .getPartitionedDmlTimeout()
              .dividedBy(10L)
              .compareTo(pdmlSettings.getStreamWatchdogCheckInterval())
          < 0) {
        pdmlSettings.setStreamWatchdogCheckInterval(
            options.getPartitionedDmlTimeout().dividedBy(10));
        pdmlSettings.setStreamWatchdogProvider(
            pdmlSettings
                .getStreamWatchdogProvider()
                .withCheckInterval(pdmlSettings.getStreamWatchdogCheckInterval()));
      }
      this.partitionedDmlStub = GrpcSpannerStub.create(pdmlSettings.build());

      this.instanceAdminStub =
          GrpcInstanceAdminStub.create(
              options
                  .getInstanceAdminStubSettings()
                  .toBuilder()
                  .setTransportChannelProvider(channelProvider)
                  .setCredentialsProvider(credentialsProvider)
                  .setStreamWatchdogProvider(watchdogProvider)
                  .build());

      this.databaseAdminStubSettings =
          options
              .getDatabaseAdminStubSettings()
              .toBuilder()
              .setTransportChannelProvider(channelProvider)
              .setCredentialsProvider(credentialsProvider)
              .setStreamWatchdogProvider(watchdogProvider)
              .build();
      this.databaseAdminStub = GrpcDatabaseAdminStub.create(this.databaseAdminStubSettings);
    } catch (Exception e) {
      throw newSpannerException(e);
    }
  }

  private static final class OperationFutureRetryAlgorithm<ResultT, MetadataT>
      implements ResultRetryAlgorithm<OperationFuture<ResultT, MetadataT>> {
    private static final ImmutableList<StatusCode.Code> RETRYABLE_CODES =
        ImmutableList.of(StatusCode.Code.DEADLINE_EXCEEDED, StatusCode.Code.UNAVAILABLE);

    @Override
    public TimedAttemptSettings createNextAttempt(
        Throwable prevThrowable,
        OperationFuture<ResultT, MetadataT> prevResponse,
        TimedAttemptSettings prevSettings) {
      // Use default retry settings.
      return null;
    }

    @Override
    public boolean shouldRetry(
        Throwable prevThrowable, OperationFuture<ResultT, MetadataT> prevResponse)
        throws CancellationException {
      if (prevThrowable instanceof ApiException) {
        ApiException e = (ApiException) prevThrowable;
        return RETRYABLE_CODES.contains(e.getStatusCode().getCode());
      }
      if (prevResponse != null) {
        try {
          prevResponse.getInitialFuture().get();
        } catch (ExecutionException ee) {
          Throwable cause = ee.getCause();
          if (cause instanceof ApiException) {
            ApiException e = (ApiException) cause;
            return RETRYABLE_CODES.contains(e.getStatusCode().getCode());
          }
        } catch (InterruptedException e) {
          return false;
        }
      }
      return false;
    }
  }

  private final class OperationFutureCallable<RequestT, ResponseT, MetadataT extends Message>
      implements Callable<OperationFuture<ResponseT, MetadataT>> {
    final OperationCallable<RequestT, ResponseT, MetadataT> operationCallable;
    final RequestT initialRequest;
    final String instanceName;
    final OperationsLister lister;
    final Function<Operation, Timestamp> getStartTimeFunction;
    Timestamp initialCallTime;
    boolean isRetry = false;

    OperationFutureCallable(
        OperationCallable<RequestT, ResponseT, MetadataT> operationCallable,
        RequestT initialRequest,
        String instanceName,
        OperationsLister lister,
        Function<Operation, Timestamp> getStartTimeFunction) {
      this.operationCallable = operationCallable;
      this.initialRequest = initialRequest;
      this.instanceName = instanceName;
      this.lister = lister;
      this.getStartTimeFunction = getStartTimeFunction;
    }

    @Override
    public OperationFuture<ResponseT, MetadataT> call() throws Exception {
      acquireAdministrativeRequestsRateLimiter();

      String operationName = null;
      if (isRetry) {
        // Query the backend to see if the operation was actually created, and that the
        // problem was caused by a network problem or other transient problem client side.
        Operation operation = mostRecentOperation(lister, getStartTimeFunction, initialCallTime);
        if (operation != null) {
          // Operation found, resume tracking that operation.
          operationName = operation.getName();
        }
      } else {
        initialCallTime =
            Timestamp.newBuilder()
                .setSeconds(
                    TimeUnit.SECONDS.convert(System.currentTimeMillis(), TimeUnit.MILLISECONDS))
                .build();
      }
      isRetry = true;

      if (operationName == null) {
        GrpcCallContext context = newCallContext(null, instanceName);
        return operationCallable.futureCall(initialRequest, context);
      } else {
        return operationCallable.resumeFutureCall(operationName);
      }
    }
  }

  private interface OperationsLister {
    Paginated<Operation> listOperations(String nextPageToken);
  }

  private Operation mostRecentOperation(
      OperationsLister lister,
      Function<Operation, Timestamp> getStartTimeFunction,
      Timestamp initialCallTime)
      throws InvalidProtocolBufferException {
    Operation res = null;
    Timestamp currMaxStartTime = null;
    String nextPageToken = null;
    Paginated<Operation> operations;
    do {
      operations = lister.listOperations(nextPageToken);
      for (Operation op : operations.getResults()) {
        Timestamp startTime = getStartTimeFunction.apply(op);
        if (res == null
            || (TimestampComparator.INSTANCE.compare(startTime, currMaxStartTime) > 0
                && TimestampComparator.INSTANCE.compare(startTime, initialCallTime) >= 0)) {
          currMaxStartTime = startTime;
          res = op;
        }
        // If the operation does not report any start time, then the operation that is not yet done
        // is the one that is the most recent.
        if (startTime == null && currMaxStartTime == null && !op.getDone()) {
          res = op;
          break;
        }
      }
    } while (operations.getNextPageToken() != null);
    return res;
  }

  private static final class TimestampComparator implements Comparator<Timestamp> {
    private static final TimestampComparator INSTANCE = new TimestampComparator();

    @Override
    public int compare(Timestamp t1, Timestamp t2) {
      if (t1 == null && t2 == null) {
        return 0;
      }
      if (t1 != null && t2 == null) {
        return 1;
      }
      if (t1 == null && t2 != null) {
        return -1;
      }
      if (t1.getSeconds() > t2.getSeconds()
          || (t1.getSeconds() == t2.getSeconds() && t1.getNanos() > t2.getNanos())) {
        return 1;
      }
      if (t1.getSeconds() < t2.getSeconds()
          || (t1.getSeconds() == t2.getSeconds() && t1.getNanos() < t2.getNanos())) {
        return -1;
      }
      return 0;
    }
  }

  private void acquireAdministrativeRequestsRateLimiter() {
    if (throttleAdministrativeRequests) {
      RateLimiter limiter = ADMINISTRATIVE_REQUESTS_RATE_LIMITERS.get(this.projectName);
      if (limiter != null) {
        limiter.acquire();
      }
    }
  }

  @Override
  public Paginated<InstanceConfig> listInstanceConfigs(int pageSize, @Nullable String pageToken)
      throws SpannerException {
    ListInstanceConfigsRequest.Builder requestBuilder =
        ListInstanceConfigsRequest.newBuilder().setParent(projectName).setPageSize(pageSize);
    if (pageToken != null) {
      requestBuilder.setPageToken(pageToken);
    }
    ListInstanceConfigsRequest request = requestBuilder.build();

    GrpcCallContext context = newCallContext(null, projectName);
    ListInstanceConfigsResponse response =
        get(instanceAdminStub.listInstanceConfigsCallable().futureCall(request, context));
    return new Paginated<>(response.getInstanceConfigsList(), response.getNextPageToken());
  }

  @Override
  public InstanceConfig getInstanceConfig(String instanceConfigName) throws SpannerException {
    GetInstanceConfigRequest request =
        GetInstanceConfigRequest.newBuilder().setName(instanceConfigName).build();

    GrpcCallContext context = newCallContext(null, projectName);
    return get(instanceAdminStub.getInstanceConfigCallable().futureCall(request, context));
  }

  @Override
  public Paginated<Instance> listInstances(
      int pageSize, @Nullable String pageToken, @Nullable String filter) throws SpannerException {
    ListInstancesRequest.Builder requestBuilder =
        ListInstancesRequest.newBuilder().setParent(projectName).setPageSize(pageSize);
    if (pageToken != null) {
      requestBuilder.setPageToken(pageToken);
    }
    if (filter != null) {
      requestBuilder.setFilter(filter);
    }
    ListInstancesRequest request = requestBuilder.build();

    GrpcCallContext context = newCallContext(null, projectName);
    ListInstancesResponse response =
        get(instanceAdminStub.listInstancesCallable().futureCall(request, context));
    return new Paginated<>(response.getInstancesList(), response.getNextPageToken());
  }

  @Override
  public OperationFuture<Instance, CreateInstanceMetadata> createInstance(
      String parent, String instanceId, Instance instance) throws SpannerException {
    CreateInstanceRequest request =
        CreateInstanceRequest.newBuilder()
            .setParent(parent)
            .setInstanceId(instanceId)
            .setInstance(instance)
            .build();
    GrpcCallContext context = newCallContext(null, parent);
    return instanceAdminStub.createInstanceOperationCallable().futureCall(request, context);
  }

  @Override
  public OperationFuture<Instance, UpdateInstanceMetadata> updateInstance(
      Instance instance, FieldMask fieldMask) throws SpannerException {
    UpdateInstanceRequest request =
        UpdateInstanceRequest.newBuilder().setInstance(instance).setFieldMask(fieldMask).build();
    GrpcCallContext context = newCallContext(null, instance.getName());
    return instanceAdminStub.updateInstanceOperationCallable().futureCall(request, context);
  }

  @Override
  public Instance getInstance(String instanceName) throws SpannerException {
    GetInstanceRequest request = GetInstanceRequest.newBuilder().setName(instanceName).build();

    GrpcCallContext context = newCallContext(null, instanceName);
    return get(instanceAdminStub.getInstanceCallable().futureCall(request, context));
  }

  @Override
  public void deleteInstance(String instanceName) throws SpannerException {
    DeleteInstanceRequest request =
        DeleteInstanceRequest.newBuilder().setName(instanceName).build();

    GrpcCallContext context = newCallContext(null, instanceName);
    get(instanceAdminStub.deleteInstanceCallable().futureCall(request, context));
  }

  @Override
  public Paginated<Operation> listBackupOperations(
      String instanceName, int pageSize, @Nullable String filter, @Nullable String pageToken) {
    acquireAdministrativeRequestsRateLimiter();
    ListBackupOperationsRequest.Builder requestBuilder =
        ListBackupOperationsRequest.newBuilder().setParent(instanceName).setPageSize(pageSize);
    if (filter != null) {
      requestBuilder.setFilter(filter);
    }
    if (pageToken != null) {
      requestBuilder.setPageToken(pageToken);
    }
    ListBackupOperationsRequest request = requestBuilder.build();

    GrpcCallContext context = newCallContext(null, instanceName);
    ListBackupOperationsResponse response =
        get(databaseAdminStub.listBackupOperationsCallable().futureCall(request, context));
    return new Paginated<>(response.getOperationsList(), response.getNextPageToken());
  }

  @Override
  public Paginated<Operation> listDatabaseOperations(
      String instanceName, int pageSize, @Nullable String filter, @Nullable String pageToken) {
    acquireAdministrativeRequestsRateLimiter();
    ListDatabaseOperationsRequest.Builder requestBuilder =
        ListDatabaseOperationsRequest.newBuilder().setParent(instanceName).setPageSize(pageSize);

    if (filter != null) {
      requestBuilder.setFilter(filter);
    }
    if (pageToken != null) {
      requestBuilder.setPageToken(pageToken);
    }
    ListDatabaseOperationsRequest request = requestBuilder.build();

    GrpcCallContext context = newCallContext(null, instanceName);
    ListDatabaseOperationsResponse response =
        get(databaseAdminStub.listDatabaseOperationsCallable().futureCall(request, context));
    return new Paginated<>(response.getOperationsList(), response.getNextPageToken());
  }

  @Override
  public Paginated<Backup> listBackups(
      String instanceName, int pageSize, @Nullable String filter, @Nullable String pageToken)
      throws SpannerException {
    acquireAdministrativeRequestsRateLimiter();
    ListBackupsRequest.Builder requestBuilder =
        ListBackupsRequest.newBuilder().setParent(instanceName).setPageSize(pageSize);
    if (filter != null) {
      requestBuilder.setFilter(filter);
    }
    if (pageToken != null) {
      requestBuilder.setPageToken(pageToken);
    }
    ListBackupsRequest request = requestBuilder.build();

    GrpcCallContext context = newCallContext(null, instanceName);
    ListBackupsResponse response =
        get(databaseAdminStub.listBackupsCallable().futureCall(request, context));
    return new Paginated<>(response.getBackupsList(), response.getNextPageToken());
  }

  @Override
  public Paginated<Database> listDatabases(
      String instanceName, int pageSize, @Nullable String pageToken) throws SpannerException {
    acquireAdministrativeRequestsRateLimiter();
    ListDatabasesRequest.Builder requestBuilder =
        ListDatabasesRequest.newBuilder().setParent(instanceName).setPageSize(pageSize);
    if (pageToken != null) {
      requestBuilder.setPageToken(pageToken);
    }
    ListDatabasesRequest request = requestBuilder.build();

    GrpcCallContext context = newCallContext(null, instanceName);
    ListDatabasesResponse response =
        get(databaseAdminStub.listDatabasesCallable().futureCall(request, context));
    return new Paginated<>(response.getDatabasesList(), response.getNextPageToken());
  }

  @Override
  public OperationFuture<Database, CreateDatabaseMetadata> createDatabase(
      final String instanceName,
      String createDatabaseStatement,
      Iterable<String> additionalStatements)
      throws SpannerException {
    final String databaseId =
        createDatabaseStatement.substring(
            "CREATE DATABASE `".length(), createDatabaseStatement.length() - 1);
    CreateDatabaseRequest request =
        CreateDatabaseRequest.newBuilder()
            .setParent(instanceName)
            .setCreateStatement(createDatabaseStatement)
            .addAllExtraStatements(additionalStatements)
            .build();

    OperationFutureCallable<CreateDatabaseRequest, Database, CreateDatabaseMetadata> callable =
        new OperationFutureCallable<CreateDatabaseRequest, Database, CreateDatabaseMetadata>(
            databaseAdminStub.createDatabaseOperationCallable(),
            request,
            instanceName,
            new OperationsLister() {
              @Override
              public Paginated<Operation> listOperations(String nextPageToken) {
                return listDatabaseOperations(
                    instanceName,
                    0,
                    String.format(
                        "(metadata.@type:type.googleapis.com/%s) AND (name:%s/operations/)",
                        CreateDatabaseMetadata.getDescriptor().getFullName(),
                        String.format("%s/databases/%s", instanceName, databaseId)),
                    nextPageToken);
              }
            },
            new Function<Operation, Timestamp>() {
              @Override
              public Timestamp apply(Operation input) {
                if (input.getDone() && input.hasResponse()) {
                  try {
                    Timestamp createTime =
                        input.getResponse().unpack(Database.class).getCreateTime();
                    if (Timestamp.getDefaultInstance().equals(createTime)) {
                      // Create time was not returned by the server (proto objects never return
                      // null, instead they return the default instance). Return null from this
                      // method to indicate that there is no known create time.
                      return null;
                    }
                  } catch (InvalidProtocolBufferException e) {
                    return null;
                  }
                }
                return null;
              }
            });
    return RetryHelper.runWithRetries(
        callable,
        databaseAdminStubSettings
            .createDatabaseOperationSettings()
            .getInitialCallSettings()
            .getRetrySettings(),
        new OperationFutureRetryAlgorithm<>(),
        NanoClock.getDefaultClock());
  }

  @Override
  public OperationFuture<Empty, UpdateDatabaseDdlMetadata> updateDatabaseDdl(
      String databaseName, Iterable<String> updateDatabaseStatements, @Nullable String updateId)
      throws SpannerException {
    acquireAdministrativeRequestsRateLimiter();
    UpdateDatabaseDdlRequest request =
        UpdateDatabaseDdlRequest.newBuilder()
            .setDatabase(databaseName)
            .addAllStatements(updateDatabaseStatements)
            .setOperationId(MoreObjects.firstNonNull(updateId, ""))
            .build();
    GrpcCallContext context = newCallContext(null, databaseName);
    OperationCallable<UpdateDatabaseDdlRequest, Empty, UpdateDatabaseDdlMetadata> callable =
        databaseAdminStub.updateDatabaseDdlOperationCallable();
    OperationFuture<Empty, UpdateDatabaseDdlMetadata> operationFuture =
        callable.futureCall(request, context);
    try {
      operationFuture.getInitialFuture().get();
    } catch (InterruptedException e) {
      throw newSpannerException(e);
    } catch (ExecutionException e) {
      Throwable t = e.getCause();
      if (t instanceof AlreadyExistsException) {
        String operationName =
            OPERATION_NAME_TEMPLATE.instantiate("database", databaseName, "operation", updateId);
        return callable.resumeFutureCall(operationName, context);
      }
    }
    return operationFuture;
  }

  @Override
  public void dropDatabase(String databaseName) throws SpannerException {
    acquireAdministrativeRequestsRateLimiter();
    DropDatabaseRequest request =
        DropDatabaseRequest.newBuilder().setDatabase(databaseName).build();

    GrpcCallContext context = newCallContext(null, databaseName);
    get(databaseAdminStub.dropDatabaseCallable().futureCall(request, context));
  }

  @Override
  public Database getDatabase(String databaseName) throws SpannerException {
    acquireAdministrativeRequestsRateLimiter();
    GetDatabaseRequest request = GetDatabaseRequest.newBuilder().setName(databaseName).build();

    GrpcCallContext context = newCallContext(null, databaseName);
    return get(databaseAdminStub.getDatabaseCallable().futureCall(request, context));
  }

  @Override
  public List<String> getDatabaseDdl(String databaseName) throws SpannerException {
    acquireAdministrativeRequestsRateLimiter();
    GetDatabaseDdlRequest request =
        GetDatabaseDdlRequest.newBuilder().setDatabase(databaseName).build();

    GrpcCallContext context = newCallContext(null, databaseName);
    return get(databaseAdminStub.getDatabaseDdlCallable().futureCall(request, context))
        .getStatementsList();
  }

  @Override
  public OperationFuture<Backup, CreateBackupMetadata> createBackup(
      final String instanceName, final String backupId, final Backup backup)
      throws SpannerException {
    CreateBackupRequest request =
        CreateBackupRequest.newBuilder()
            .setParent(instanceName)
            .setBackupId(backupId)
            .setBackup(backup)
            .build();
    OperationFutureCallable<CreateBackupRequest, Backup, CreateBackupMetadata> callable =
        new OperationFutureCallable<CreateBackupRequest, Backup, CreateBackupMetadata>(
            databaseAdminStub.createBackupOperationCallable(),
            request,
            instanceName,
            new OperationsLister() {
              @Override
              public Paginated<Operation> listOperations(String nextPageToken) {
                return listBackupOperations(
                    instanceName,
                    0,
                    String.format(
                        "(metadata.@type:type.googleapis.com/%s) AND (metadata.name:%s)",
                        CreateBackupMetadata.getDescriptor().getFullName(),
                        String.format("%s/backups/%s", instanceName, backupId)),
                    nextPageToken);
              }
            },
            new Function<Operation, Timestamp>() {
              @Override
              public Timestamp apply(Operation input) {
                try {
                  return input
                      .getMetadata()
                      .unpack(CreateBackupMetadata.class)
                      .getProgress()
                      .getStartTime();
                } catch (InvalidProtocolBufferException e) {
                  return null;
                }
              }
            });
    return RetryHelper.runWithRetries(
        callable,
        databaseAdminStubSettings
            .createBackupOperationSettings()
            .getInitialCallSettings()
            .getRetrySettings(),
        new OperationFutureRetryAlgorithm<>(),
        NanoClock.getDefaultClock());
  }

  @Override
  public OperationFuture<Database, RestoreDatabaseMetadata> restoreDatabase(
      final String databaseInstanceName, final String databaseId, String backupName) {
    RestoreDatabaseRequest request =
        RestoreDatabaseRequest.newBuilder()
            .setParent(databaseInstanceName)
            .setDatabaseId(databaseId)
            .setBackup(backupName)
            .build();

    OperationFutureCallable<RestoreDatabaseRequest, Database, RestoreDatabaseMetadata> callable =
        new OperationFutureCallable<RestoreDatabaseRequest, Database, RestoreDatabaseMetadata>(
            databaseAdminStub.restoreDatabaseOperationCallable(),
            request,
            databaseInstanceName,
            new OperationsLister() {
              @Override
              public Paginated<Operation> listOperations(String nextPageToken) {
                return listDatabaseOperations(
                    databaseInstanceName,
                    0,
                    String.format(
                        "(metadata.@type:type.googleapis.com/%s) AND (metadata.name:%s)",
                        RestoreDatabaseMetadata.getDescriptor().getFullName(),
                        String.format("%s/databases/%s", databaseInstanceName, databaseId)),
                    nextPageToken);
              }
            },
            new Function<Operation, Timestamp>() {
              @Override
              public Timestamp apply(Operation input) {
                try {
                  return input
                      .getMetadata()
                      .unpack(RestoreDatabaseMetadata.class)
                      .getProgress()
                      .getStartTime();
                } catch (InvalidProtocolBufferException e) {
                  return null;
                }
              }
            });
    return RetryHelper.runWithRetries(
        callable,
        databaseAdminStubSettings
            .restoreDatabaseOperationSettings()
            .getInitialCallSettings()
            .getRetrySettings(),
        new OperationFutureRetryAlgorithm<>(),
        NanoClock.getDefaultClock());
  }

  @Override
  public Backup updateBackup(Backup backup, FieldMask updateMask) {
    acquireAdministrativeRequestsRateLimiter();
    UpdateBackupRequest request =
        UpdateBackupRequest.newBuilder().setBackup(backup).setUpdateMask(updateMask).build();
    GrpcCallContext context = newCallContext(null, backup.getName());
    return databaseAdminStub.updateBackupCallable().call(request, context);
  }

  @Override
  public void deleteBackup(String backupName) {
    acquireAdministrativeRequestsRateLimiter();
    DeleteBackupRequest request = DeleteBackupRequest.newBuilder().setName(backupName).build();
    GrpcCallContext context = newCallContext(null, backupName);
    databaseAdminStub.deleteBackupCallable().call(request, context);
  }

  @Override
  public Backup getBackup(String backupName) throws SpannerException {
    acquireAdministrativeRequestsRateLimiter();
    GetBackupRequest request = GetBackupRequest.newBuilder().setName(backupName).build();
    GrpcCallContext context = newCallContext(null, backupName);
    return get(databaseAdminStub.getBackupCallable().futureCall(request, context));
  }

  @Override
  public Operation getOperation(String name) throws SpannerException {
    acquireAdministrativeRequestsRateLimiter();
    GetOperationRequest request = GetOperationRequest.newBuilder().setName(name).build();
    GrpcCallContext context = newCallContext(null, name);
    return get(
        databaseAdminStub.getOperationsStub().getOperationCallable().futureCall(request, context));
  }

  @Override
  public void cancelOperation(String name) throws SpannerException {
    acquireAdministrativeRequestsRateLimiter();
    CancelOperationRequest request = CancelOperationRequest.newBuilder().setName(name).build();
    GrpcCallContext context = newCallContext(null, name);
    get(
        databaseAdminStub
            .getOperationsStub()
            .cancelOperationCallable()
            .futureCall(request, context));
  }

  @Override
  public List<Session> batchCreateSessions(
      String databaseName,
      int sessionCount,
      @Nullable Map<String, String> labels,
      @Nullable Map<Option, ?> options)
      throws SpannerException {
    BatchCreateSessionsRequest.Builder requestBuilder =
        BatchCreateSessionsRequest.newBuilder()
            .setDatabase(databaseName)
            .setSessionCount(sessionCount);
    if (labels != null && !labels.isEmpty()) {
      Session.Builder session = Session.newBuilder().putAllLabels(labels);
      requestBuilder.setSessionTemplate(session);
    }
    BatchCreateSessionsRequest request = requestBuilder.build();
    GrpcCallContext context = newCallContext(options, databaseName);
    return get(spannerStub.batchCreateSessionsCallable().futureCall(request, context))
        .getSessionList();
  }

  @Override
  public Session createSession(
      String databaseName, @Nullable Map<String, String> labels, @Nullable Map<Option, ?> options)
      throws SpannerException {
    CreateSessionRequest.Builder requestBuilder =
        CreateSessionRequest.newBuilder().setDatabase(databaseName);
    if (labels != null && !labels.isEmpty()) {
      Session.Builder session = Session.newBuilder().putAllLabels(labels);
      requestBuilder.setSession(session);
    }
    CreateSessionRequest request = requestBuilder.build();
    GrpcCallContext context = newCallContext(options, databaseName);
    return get(spannerStub.createSessionCallable().futureCall(request, context));
  }

  @Override
  public void deleteSession(String sessionName, @Nullable Map<Option, ?> options)
      throws SpannerException {
    get(asyncDeleteSession(sessionName, options));
  }

  @Override
  public ApiFuture<Empty> asyncDeleteSession(String sessionName, @Nullable Map<Option, ?> options) {
    DeleteSessionRequest request = DeleteSessionRequest.newBuilder().setName(sessionName).build();
    GrpcCallContext context = newCallContext(options, sessionName);
    return spannerStub.deleteSessionCallable().futureCall(request, context);
  }

  @Override
  public StreamingCall read(
      ReadRequest request, ResultStreamConsumer consumer, @Nullable Map<Option, ?> options) {
    GrpcCallContext context = newCallContext(options, request.getSession());
    SpannerResponseObserver responseObserver = new SpannerResponseObserver(consumer);
    spannerStub.streamingReadCallable().call(request, responseObserver, context);
    final StreamController controller = responseObserver.getController();
    return new StreamingCall() {
      @Override
      public void request(int numMessage) {
        controller.request(numMessage);
      }

      // TODO(hzyi): streamController currently does not support cancel with message. Add
      // this in gax and update this method later
      @Override
      public void cancel(String message) {
        controller.cancel();
      }
    };
  }

  @Override
  public ResultSet executeQuery(ExecuteSqlRequest request, @Nullable Map<Option, ?> options) {
    return get(executeQueryAsync(request, options));
  }

  @Override
  public ApiFuture<ResultSet> executeQueryAsync(
      ExecuteSqlRequest request, @Nullable Map<Option, ?> options) {
    GrpcCallContext context = newCallContext(options, request.getSession());
    return spannerStub.executeSqlCallable().futureCall(request, context);
  }

  @Override
  public ResultSet executePartitionedDml(
      ExecuteSqlRequest request, @Nullable Map<Option, ?> options) {
    GrpcCallContext context = newCallContext(options, request.getSession());
    return get(partitionedDmlStub.executeSqlCallable().futureCall(request, context));
  }

  @Override
  public RetrySettings getPartitionedDmlRetrySettings() {
    return partitionedDmlRetrySettings;
  }

  @Override
  public ServerStream<PartialResultSet> executeStreamingPartitionedDml(
      ExecuteSqlRequest request, Map<Option, ?> options, Duration timeout) {
    GrpcCallContext context = newCallContext(options, request.getSession());
    context = context.withStreamWaitTimeout(timeout);
    return partitionedDmlStub.executeStreamingSqlCallable().call(request, context);
  }

  @Override
  public StreamingCall executeQuery(
      ExecuteSqlRequest request, ResultStreamConsumer consumer, @Nullable Map<Option, ?> options) {
    GrpcCallContext context = newCallContext(options, request.getSession());
    SpannerResponseObserver responseObserver = new SpannerResponseObserver(consumer);
    spannerStub.executeStreamingSqlCallable().call(request, responseObserver, context);
    final StreamController controller = responseObserver.getController();
    return new StreamingCall() {
      @Override
      public void request(int numMessage) {
        controller.request(numMessage);
      }

      // TODO(hzyi): streamController currently does not support cancel with message. Add
      // this in gax and update this method later
      @Override
      public void cancel(String message) {
        controller.cancel();
      }
    };
  }

  @Override
  public ExecuteBatchDmlResponse executeBatchDml(
      ExecuteBatchDmlRequest request, @Nullable Map<Option, ?> options) {
    return get(executeBatchDmlAsync(request, options));
  }

  @Override
  public ApiFuture<ExecuteBatchDmlResponse> executeBatchDmlAsync(
      ExecuteBatchDmlRequest request, @Nullable Map<Option, ?> options) {
    GrpcCallContext context = newCallContext(options, request.getSession());
    return spannerStub.executeBatchDmlCallable().futureCall(request, context);
  }

  @Override
  public ApiFuture<Transaction> beginTransactionAsync(
      BeginTransactionRequest request, @Nullable Map<Option, ?> options) {
    GrpcCallContext context = newCallContext(options, request.getSession());
    return spannerStub.beginTransactionCallable().futureCall(request, context);
  }

  @Override
  public Transaction beginTransaction(
      BeginTransactionRequest request, @Nullable Map<Option, ?> options) throws SpannerException {
    return get(beginTransactionAsync(request, options));
  }

  @Override
  public ApiFuture<CommitResponse> commitAsync(
      CommitRequest commitRequest, @Nullable Map<Option, ?> options) {
    GrpcCallContext context = newCallContext(options, commitRequest.getSession());
    return spannerStub.commitCallable().futureCall(commitRequest, context);
  }

  @Override
  public CommitResponse commit(CommitRequest commitRequest, @Nullable Map<Option, ?> options)
      throws SpannerException {
    return get(commitAsync(commitRequest, options));
  }

  @Override
  public ApiFuture<Empty> rollbackAsync(RollbackRequest request, @Nullable Map<Option, ?> options) {
    GrpcCallContext context = newCallContext(options, request.getSession());
    return spannerStub.rollbackCallable().futureCall(request, context);
  }

  @Override
  public void rollback(RollbackRequest request, @Nullable Map<Option, ?> options)
      throws SpannerException {
    get(rollbackAsync(request, options));
  }

  @Override
  public PartitionResponse partitionQuery(
      PartitionQueryRequest request, @Nullable Map<Option, ?> options) throws SpannerException {
    GrpcCallContext context = newCallContext(options, request.getSession());
    return get(spannerStub.partitionQueryCallable().futureCall(request, context));
  }

  @Override
  public PartitionResponse partitionRead(
      PartitionReadRequest request, @Nullable Map<Option, ?> options) throws SpannerException {
    GrpcCallContext context = newCallContext(options, request.getSession());
    return get(spannerStub.partitionReadCallable().futureCall(request, context));
  }

  @Override
  public Policy getDatabaseAdminIAMPolicy(String resource) {
    acquireAdministrativeRequestsRateLimiter();
    GrpcCallContext context = newCallContext(null, resource);
    return get(
        databaseAdminStub
            .getIamPolicyCallable()
            .futureCall(GetIamPolicyRequest.newBuilder().setResource(resource).build(), context));
  }

  @Override
  public Policy setDatabaseAdminIAMPolicy(String resource, Policy policy) {
    acquireAdministrativeRequestsRateLimiter();
    GrpcCallContext context = newCallContext(null, resource);
    return get(
        databaseAdminStub
            .setIamPolicyCallable()
            .futureCall(
                SetIamPolicyRequest.newBuilder().setResource(resource).setPolicy(policy).build(),
                context));
  }

  @Override
  public TestIamPermissionsResponse testDatabaseAdminIAMPermissions(
      String resource, Iterable<String> permissions) {
    acquireAdministrativeRequestsRateLimiter();
    GrpcCallContext context = newCallContext(null, resource);
    return get(
        databaseAdminStub
            .testIamPermissionsCallable()
            .futureCall(
                TestIamPermissionsRequest.newBuilder()
                    .setResource(resource)
                    .addAllPermissions(permissions)
                    .build(),
                context));
  }

  @Override
  public Policy getInstanceAdminIAMPolicy(String resource) {
    acquireAdministrativeRequestsRateLimiter();
    GrpcCallContext context = newCallContext(null, resource);
    return get(
        instanceAdminStub
            .getIamPolicyCallable()
            .futureCall(GetIamPolicyRequest.newBuilder().setResource(resource).build(), context));
  }

  @Override
  public Policy setInstanceAdminIAMPolicy(String resource, Policy policy) {
    acquireAdministrativeRequestsRateLimiter();
    GrpcCallContext context = newCallContext(null, resource);
    return get(
        instanceAdminStub
            .setIamPolicyCallable()
            .futureCall(
                SetIamPolicyRequest.newBuilder().setResource(resource).setPolicy(policy).build(),
                context));
  }

  @Override
  public TestIamPermissionsResponse testInstanceAdminIAMPermissions(
      String resource, Iterable<String> permissions) {
    acquireAdministrativeRequestsRateLimiter();
    GrpcCallContext context = newCallContext(null, resource);
    return get(
        instanceAdminStub
            .testIamPermissionsCallable()
            .futureCall(
                TestIamPermissionsRequest.newBuilder()
                    .setResource(resource)
                    .addAllPermissions(permissions)
                    .build(),
                context));
  }

  /** Gets the result of an async RPC call, handling any exceptions encountered. */
  private static <T> T get(final Future<T> future) throws SpannerException {
    final Context context = Context.current();
    try {
      return future.get();
    } catch (InterruptedException e) {
      // We are the sole consumer of the future, so cancel it.
      future.cancel(true);
      throw SpannerExceptionFactory.propagateInterrupt(e);
    } catch (Exception e) {
      throw newSpannerException(context, e);
    }
  }

  @VisibleForTesting
  GrpcCallContext newCallContext(@Nullable Map<Option, ?> options, String resource) {
    GrpcCallContext context = GrpcCallContext.createDefault();
    if (options != null) {
      context = context.withChannelAffinity(Option.CHANNEL_HINT.getLong(options).intValue());
    }
    context = context.withExtraHeaders(metadataProvider.newExtraHeaders(resource, projectName));
    if (callCredentialsProvider != null) {
      CallCredentials callCredentials = callCredentialsProvider.getCallCredentials();
      if (callCredentials != null) {
        context =
            context.withCallOptions(context.getCallOptions().withCallCredentials(callCredentials));
      }
    }
    return context.withStreamWaitTimeout(waitTimeout).withStreamIdleTimeout(idleTimeout);
  }

  @Override
  public void shutdown() {
    this.rpcIsClosed = true;
    this.spannerStub.close();
    this.partitionedDmlStub.close();
    this.instanceAdminStub.close();
    this.databaseAdminStub.close();
    this.spannerWatchdog.shutdown();
    this.executorProvider.shutdown();

    try {
      this.spannerStub.awaitTermination(10L, TimeUnit.SECONDS);
      this.partitionedDmlStub.awaitTermination(10L, TimeUnit.SECONDS);
      this.instanceAdminStub.awaitTermination(10L, TimeUnit.SECONDS);
      this.databaseAdminStub.awaitTermination(10L, TimeUnit.SECONDS);
      this.spannerWatchdog.awaitTermination(10L, TimeUnit.SECONDS);
      this.executorProvider.awaitTermination();
    } catch (InterruptedException e) {
      throw SpannerExceptionFactory.propagateInterrupt(e);
    }
  }

  @Override
  public boolean isClosed() {
    return rpcIsClosed;
  }

  /**
   * A {@code ResponseObserver} that exposes the {@code StreamController} and delegates callbacks to
   * the {@link ResultStreamConsumer}.
   */
  private static class SpannerResponseObserver implements ResponseObserver<PartialResultSet> {
    private StreamController controller;
    private final ResultStreamConsumer consumer;

    public SpannerResponseObserver(ResultStreamConsumer consumer) {
      this.consumer = consumer;
    }

    @Override
    public void onStart(StreamController controller) {

      // Disable the auto flow control to allow client library
      // set the number of messages it prefers to request
      controller.disableAutoInboundFlowControl();
      this.controller = controller;
    }

    @Override
    public void onResponse(PartialResultSet response) {
      consumer.onPartialResultSet(response);
    }

    @Override
    public void onError(Throwable t) {
      consumer.onError(newSpannerException(t));
    }

    @Override
    public void onComplete() {
      consumer.onCompleted();
    }

    StreamController getController() {
      return Preconditions.checkNotNull(this.controller);
    }
  }

  private static Duration systemProperty(String name, int defaultValue) {
    String stringValue = System.getProperty(name, "");
    return Duration.ofSeconds(stringValue.isEmpty() ? defaultValue : Integer.parseInt(stringValue));
  }
}
