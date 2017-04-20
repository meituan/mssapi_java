/*
 * Copyright 2010-2014 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package com.amazonaws.services.s3;

import com.amazonaws.*;
import com.amazonaws.AmazonServiceException.ErrorType;
import com.amazonaws.auth.*;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.event.ProgressEventType;
import com.amazonaws.event.ProgressInputStream;
import com.amazonaws.event.ProgressListener;
import com.amazonaws.handlers.HandlerChainFactory;
import com.amazonaws.handlers.RequestHandler2;
import com.amazonaws.http.ExecutionContext;
import com.amazonaws.http.HttpMethodName;
import com.amazonaws.http.HttpResponseHandler;
import com.amazonaws.internal.ResettableInputStream;
import com.amazonaws.internal.StaticCredentialsProvider;
import com.amazonaws.metrics.AwsSdkMetrics;
import com.amazonaws.metrics.RequestMetricCollector;
import com.amazonaws.regions.RegionUtils;
import com.amazonaws.services.s3.internal.*;
import com.amazonaws.services.s3.metrics.S3ServiceMetric;
import com.amazonaws.services.s3.model.*;
import com.amazonaws.services.s3.model.transform.*;
import com.amazonaws.services.s3.model.transform.XmlResponsesSaxParser.CompleteMultipartUploadHandler;
import com.amazonaws.services.s3.model.transform.XmlResponsesSaxParser.CopyObjectResultHandler;
import com.amazonaws.transform.Unmarshaller;
import com.amazonaws.util.*;
import com.amazonaws.util.AWSRequestMetrics.Field;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.client.methods.HttpRequestBase;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.Map.Entry;
import java.util.regex.Matcher;

import com.amazonaws.util.Base64;

import static com.amazonaws.event.SDKProgressPublisher.publishProgress;
import static com.amazonaws.internal.ResettableInputStream.newResettableInputStream;
import static com.amazonaws.services.s3.model.S3DataSource.Utils.cleanupDataSource;
import static com.amazonaws.util.LengthCheckInputStream.EXCLUDE_SKIPPED_BYTES;
import static com.amazonaws.util.LengthCheckInputStream.INCLUDE_SKIPPED_BYTES;
import static com.amazonaws.util.Throwables.failure;

/**
 * <p>
 * Provides the client for accessing the Amazon S3 web service.
 * </p>
 * <p>
 * Amazon S3 provides storage for the Internet,
 * and is designed to make web-scale computing easier for developers.
 * </p>
 * <p>
 * The Amazon S3 Java Client provides a simple interface that can be
 * used to store and retrieve any amount of data, at any time,
 * from anywhere on the web. It gives any developer access to the same
 * highly scalable, reliable, secure, fast, inexpensive infrastructure
 * that Amazon uses to run its own global network of web sites.
 * The service aims to maximize benefits of scale and to pass those
 * benefits on to developers.
 * </p>
 * <p>
 * For more information about Amazon S3, please see
 * <a href="http://aws.amazon.com/s3">
 * http://aws.amazon.com/s3</a>
 * </p>
 */
public class AmazonS3Client extends AmazonWebServiceClient implements AmazonS3 {

    public static final String S3_SERVICE_NAME = "s3";

    private static final String S3_SIGNER = "S3SignerType";
    private static final String S3_V4_SIGNER = "AWSS3V4SignerType";

    /** Shared logger for client events */
    private static Log log = LogFactory.getLog(AmazonS3Client.class);

    static {
        // Enable S3 specific predefined request metrics.
        AwsSdkMetrics.addAll(Arrays.asList(S3ServiceMetric.values()));

        // Register S3-specific signers.
        SignerFactory.registerSigner(S3_SIGNER, S3Signer.class);
        SignerFactory.registerSigner(S3_V4_SIGNER, AWSS3V4Signer.class);
    }

    /** Responsible for handling error responses from all S3 service calls. */
    private final S3ErrorResponseHandler errorResponseHandler = new S3ErrorResponseHandler();

    /** Shared response handler for operations with no response.  */
    private final S3XmlResponseHandler<Void> voidResponseHandler = new S3XmlResponseHandler<Void>(null);

    /** Shared factory for converting configuration objects to XML */
    private static final BucketConfigurationXmlFactory bucketConfigurationXmlFactory = new BucketConfigurationXmlFactory();

    /** Shared factory for converting request payment configuration objects to XML */
    private static final RequestPaymentConfigurationXmlFactory requestPaymentConfigurationXmlFactory = new RequestPaymentConfigurationXmlFactory();

    /** S3 specific client configuration options */
    private S3ClientOptions clientOptions = new S3ClientOptions();

    /** Provider for AWS credentials. */
    private final AWSCredentialsProvider awsCredentialsProvider;

    /** Whether or not this client has an explicit region configured. */
    private boolean hasExplicitRegion;

    /**
     * Constructs a new client to invoke service methods on Amazon S3. A
     * credentials provider chain will be used that searches for credentials in
     * this order:
     * <ul>
     * <li>Environment Variables - AWS_ACCESS_KEY_ID and AWS_SECRET_KEY</li>
     * <li>Java System Properties - aws.accessKeyId and aws.secretKey</li>
     * <li>Credential profiles file at the default location (~/.aws/credentials) shared by all AWS SDKs and the AWS CLI</li>
     * <li>Instance Profile Credentials - delivered through the Amazon EC2
     * metadata service</li>
     * </ul>
     *
     * <p>
     * If no credentials are found in the chain, this client will attempt to
     * work in an anonymous mode where requests aren't signed. Only a subset of
     * the Amazon S3 API will work with anonymous <i>(i.e. unsigned)</i> requests,
     * but this can prove useful in some situations. For example:
     * <ul>
     * <li>If an Amazon S3 bucket has {@link Permission#Read} permission for the
     * {@link GroupGrantee#AllUsers} group, anonymous clients can call
     * {@link #listObjects(String)} to see what objects are stored in a bucket.</li>
     * <li>If an object has {@link Permission#Read} permission for the
     * {@link GroupGrantee#AllUsers} group, anonymous clients can call
     * {@link #getObject(String, String)} and
     * {@link #getObjectMetadata(String, String)} to pull object content and
     * metadata.</li>
     * <li>If a bucket has {@link Permission#Write} permission for the
     * {@link GroupGrantee#AllUsers} group, anonymous clients can upload objects
     * to the bucket.</li>
     * </ul>
     * </p>
     * <p>
     * You can force the client to operate in an anonymous mode, and skip the credentials
     * provider chain, by passing in <code>null</code> for the credentials.
     * </p>
     *
     * @see AmazonS3Client#AmazonS3Client(AWSCredentials)
     * @see AmazonS3Client#AmazonS3Client(AWSCredentials, ClientConfiguration)
     */
    public AmazonS3Client() {
        this(new AWSCredentialsProviderChain(
                new EnvironmentVariableCredentialsProvider(),
                new SystemPropertiesCredentialsProvider(),
                new ProfileCredentialsProvider(),
                new InstanceProfileCredentialsProvider()) {

            public AWSCredentials getCredentials() {
                try {
                    return super.getCredentials();
                } catch (AmazonClientException ace) {}

                log.debug("No credentials available; falling back to anonymous access");
                return null;
            }
        });
    }

    /**
     * Constructs a new Amazon S3 client using the specified AWS credentials to
     * access Amazon S3.
     *
     * @param awsCredentials
     *            The AWS credentials to use when making requests to Amazon S3
     *            with this client.
     *
     * @see AmazonS3Client#AmazonS3Client()
     * @see AmazonS3Client#AmazonS3Client(AWSCredentials, ClientConfiguration)
     */
    public AmazonS3Client(AWSCredentials awsCredentials) {
        this(awsCredentials, new ClientConfiguration());
    }

    /**
     * Constructs a new Amazon S3 client using the specified AWS credentials and
     * client configuration to access Amazon S3.
     *
     * @param awsCredentials
     *            The AWS credentials to use when making requests to Amazon S3
     *            with this client.
     * @param clientConfiguration
     *            The client configuration options controlling how this client
     *            connects to Amazon S3 (e.g. proxy settings, retry counts, etc).
     *
     * @see AmazonS3Client#AmazonS3Client()
     * @see AmazonS3Client#AmazonS3Client(AWSCredentials)
     */
    public AmazonS3Client(AWSCredentials awsCredentials, ClientConfiguration clientConfiguration) {
        super(clientConfiguration);
        this.awsCredentialsProvider = new StaticCredentialsProvider(awsCredentials);
        init();
    }

    /**
     * Constructs a new Amazon S3 client using the specified AWS credentials
     * provider to access Amazon S3.
     *
     * @param credentialsProvider
     *            The AWS credentials provider which will provide credentials
     *            to authenticate requests with AWS services.
     */
    public AmazonS3Client(AWSCredentialsProvider credentialsProvider) {
        this(credentialsProvider, new ClientConfiguration());
    }

    /**
     * Constructs a new Amazon S3 client using the specified AWS credentials and
     * client configuration to access Amazon S3.
     *
     * @param credentialsProvider
     *            The AWS credentials provider which will provide credentials
     *            to authenticate requests with AWS services.
     * @param clientConfiguration
     *            The client configuration options controlling how this client
     *            connects to Amazon S3 (e.g. proxy settings, retry counts, etc).
     */
    public AmazonS3Client(AWSCredentialsProvider credentialsProvider,
            ClientConfiguration clientConfiguration) {
        this(credentialsProvider, clientConfiguration, null);
    }

    /**
     * Constructs a new Amazon S3 client using the specified AWS credentials,
     * client configuration and request metric collector to access Amazon S3.
     *
     * @param credentialsProvider
     *            The AWS credentials provider which will provide credentials
     *            to authenticate requests with AWS services.
     * @param clientConfiguration
     *            The client configuration options controlling how this client
     *            connects to Amazon S3 (e.g. proxy settings, retry counts, etc).
     * @param requestMetricCollector request metric collector
     */
    public AmazonS3Client(AWSCredentialsProvider credentialsProvider,
            ClientConfiguration clientConfiguration,
            RequestMetricCollector requestMetricCollector) {
        super(clientConfiguration, requestMetricCollector);
        this.awsCredentialsProvider = credentialsProvider;
        init();
    }

    /**
     * Constructs a new client using the specified client configuration to
     * access Amazon S3. A credentials provider chain will be used that searches
     * for credentials in this order:
     * <ul>
     * <li>Environment Variables - AWS_ACCESS_KEY_ID and AWS_SECRET_KEY</li>
     * <li>Java System Properties - aws.accessKeyId and aws.secretKey</li>
     * <li>Instance Profile Credentials - delivered through the Amazon EC2
     * metadata service</li>
     * </ul>
     *
     * <p>
     * If no credentials are found in the chain, this client will attempt to
     * work in an anonymous mode where requests aren't signed. Only a subset of
     * the Amazon S3 API will work with anonymous <i>(i.e. unsigned)</i>
     * requests, but this can prove useful in some situations. For example:
     * <ul>
     * <li>If an Amazon S3 bucket has {@link Permission#Read} permission for the
     * {@link GroupGrantee#AllUsers} group, anonymous clients can call
     * {@link #listObjects(String)} to see what objects are stored in a bucket.</li>
     * <li>If an object has {@link Permission#Read} permission for the
     * {@link GroupGrantee#AllUsers} group, anonymous clients can call
     * {@link #getObject(String, String)} and
     * {@link #getObjectMetadata(String, String)} to pull object content and
     * metadata.</li>
     * <li>If a bucket has {@link Permission#Write} permission for the
     * {@link GroupGrantee#AllUsers} group, anonymous clients can upload objects
     * to the bucket.</li>
     * </ul>
     * </p>
     * <p>
     * You can force the client to operate in an anonymous mode, and skip the
     * credentials provider chain, by passing in <code>null</code> for the
     * credentials.
     * </p>
     *
     * @param clientConfiguration
     *            The client configuration options controlling how this client
     *            connects to Amazon S3 (e.g. proxy settings, retry counts, etc).
     *
     * @see AmazonS3Client#AmazonS3Client(AWSCredentials)
     * @see AmazonS3Client#AmazonS3Client(AWSCredentials, ClientConfiguration)
     */
    public AmazonS3Client(ClientConfiguration clientConfiguration) {
        this(new DefaultAWSCredentialsProviderChain(), clientConfiguration);
    }

    private void init() {
        // Because of S3's virtual host style addressing, we need to change the
        // default, strict hostname verification to be more lenient.
        client.disableStrictHostnameVerification();

        // calling this.setEndpoint(...) will also modify the signer accordingly
        setEndpoint(Constants.S3_HOSTNAME);

        HandlerChainFactory chainFactory = new HandlerChainFactory();
        requestHandler2s.addAll(chainFactory.newRequestHandlerChain(
                "/com/amazonaws/services/s3/request.handlers"));
        requestHandler2s.addAll(chainFactory.newRequestHandler2Chain(
                "/com/amazonaws/services/s3/request.handler2s"));
    }

    @Override
    public void setEndpoint(String endpoint) {
        /*
         * When signing requests using a pre-Signature-Version-4 signer, it's
         * possible to use the endpoint "s3.amazonaws.com" to access buckets in
         * any region - we send the request to &lt;bucket&gt;.s3.amazonaws.com,
         * which resolves to an S3 endpoint in the appropriate region.
         *
         * However, when the user opts in to using Signature Version 4, we need
         * to include the region of the bucket in the signature, and cannot
         * take advantage of this handy feature of S3.
         *
         * If you want to use Signature Version 4 to access a bucket in the
         * US Classic region (which does not have a region-specific endpoint),
         * you'll need to call setRegion(Region.getRegion(Regions.US_EAST_1))
         * to explicitly tell us which region to include in the signature.
         */

        hasExplicitRegion = !(Constants.S3_HOSTNAME.equals(endpoint));
        super.setEndpoint(endpoint);
    }

    @Override
    public void setRegion(com.amazonaws.regions.Region region) {
        hasExplicitRegion = true;
        super.setRegion(region);
    }


    /**
     * <p>
     * Override the default S3 client options for this client.
     * </p>
     * @param clientOptions
     *            The S3 client options to use.
     */
    public void setS3ClientOptions(S3ClientOptions clientOptions) {
        this.clientOptions = new S3ClientOptions(clientOptions);
    }

    /* (non-Javadoc)
     * @see com.amazonaws.services.s3.AmazonS3#listNextBatchOfVersions(com.amazonaws.services.s3.model.S3VersionListing)
     */
    public VersionListing listNextBatchOfVersions(VersionListing previousVersionListing)
            throws AmazonClientException, AmazonServiceException {
        assertParameterNotNull(previousVersionListing,
            "The previous version listing parameter must be specified when listing the next batch of versions in a bucket");

        if (!previousVersionListing.isTruncated()) {
            VersionListing emptyListing = new VersionListing();
            emptyListing.setBucketName(previousVersionListing.getBucketName());
            emptyListing.setDelimiter(previousVersionListing.getDelimiter());
            emptyListing.setKeyMarker(previousVersionListing.getNextKeyMarker());
            emptyListing.setVersionIdMarker(previousVersionListing.getNextVersionIdMarker());
            emptyListing.setMaxKeys(previousVersionListing.getMaxKeys());
            emptyListing.setPrefix(previousVersionListing.getPrefix());
            emptyListing.setEncodingType(previousVersionListing.getEncodingType());
            emptyListing.setTruncated(false);

            return emptyListing;
        }

        return listVersions(new ListVersionsRequest(
                previousVersionListing.getBucketName(),
                previousVersionListing.getPrefix(),
                previousVersionListing.getNextKeyMarker(),
                previousVersionListing.getNextVersionIdMarker(),
                previousVersionListing.getDelimiter(),
                Integer.valueOf(previousVersionListing.getMaxKeys()))
                .withEncodingType(previousVersionListing.getEncodingType()));
    }

    /* (non-Javadoc)
     * @see com.amazonaws.services.s3.AmazonS3#listVersions(java.lang.String, java.lang.String)
     */
    public VersionListing listVersions(String bucketName, String prefix)
            throws AmazonClientException, AmazonServiceException {
        return listVersions(new ListVersionsRequest(bucketName, prefix, null, null, null, null));
    }

    /* (non-Javadoc)
     * @see com.amazonaws.services.s3.AmazonS3#listVersions(java.lang.String, java.lang.String, java.lang.String, java.lang.String, java.lang.String, java.lang.Integer)
     */
    public VersionListing listVersions(String bucketName, String prefix, String keyMarker, String versionIdMarker, String delimiter, Integer maxKeys)
            throws AmazonClientException, AmazonServiceException {

        ListVersionsRequest request = new ListVersionsRequest()
            .withBucketName(bucketName)
            .withPrefix(prefix)
            .withDelimiter(delimiter)
            .withKeyMarker(keyMarker)
            .withVersionIdMarker(versionIdMarker)
            .withMaxResults(maxKeys);
        return listVersions(request);
    }

    /* (non-Javadoc)
     * @see com.amazonaws.services.s3.AmazonS3#listVersions(com.amazonaws.services.s3.model.ListVersionsRequest)
     */
    public VersionListing listVersions(ListVersionsRequest listVersionsRequest)
            throws AmazonClientException, AmazonServiceException {
        assertParameterNotNull(listVersionsRequest.getBucketName(), "The bucket name parameter must be specified when listing versions in a bucket");

        Request<ListVersionsRequest> request = createRequest(listVersionsRequest.getBucketName(), null, listVersionsRequest, HttpMethodName.GET);
        request.addParameter("versions", null);

        if (listVersionsRequest.getPrefix() != null) request.addParameter("prefix", listVersionsRequest.getPrefix());
        if (listVersionsRequest.getKeyMarker() != null) request.addParameter("key-marker", listVersionsRequest.getKeyMarker());
        if (listVersionsRequest.getVersionIdMarker() != null) request.addParameter("version-id-marker", listVersionsRequest.getVersionIdMarker());
        if (listVersionsRequest.getDelimiter() != null) request.addParameter("delimiter", listVersionsRequest.getDelimiter());
        if (listVersionsRequest.getMaxResults() != null && listVersionsRequest.getMaxResults().intValue() >= 0) request.addParameter("max-keys", listVersionsRequest.getMaxResults().toString());
        if (listVersionsRequest.getEncodingType() != null) request.addParameter("encoding-type", listVersionsRequest.getEncodingType());

        return invoke(request, new Unmarshallers.VersionListUnmarshaller(), listVersionsRequest.getBucketName(), null);
    }

    /* (non-Javadoc)
     * @see com.amazonaws.services.s3.AmazonS3#listObjects(java.lang.String)
     */
    public ObjectListing listObjects(String bucketName)
            throws AmazonClientException, AmazonServiceException {
        return listObjects(new ListObjectsRequest(bucketName, null, null, null, null));
    }

    /* (non-Javadoc)
     * @see com.amazonaws.services.s3.AmazonS3#listObjects(java.lang.String, java.lang.String)
     */
    public ObjectListing listObjects(String bucketName, String prefix)
            throws AmazonClientException, AmazonServiceException {
        return listObjects(new ListObjectsRequest(bucketName, prefix, null, null, null));
    }

    /* (non-Javadoc)
     * @see com.amazonaws.services.s3.AmazonS3#listObjects(com.amazonaws.services.s3.model.ListObjectsRequest)
     */
    public ObjectListing listObjects(ListObjectsRequest listObjectsRequest)
            throws AmazonClientException, AmazonServiceException {
        assertParameterNotNull(listObjectsRequest.getBucketName(), "The bucket name parameter must be specified when listing objects in a bucket");

        Request<ListObjectsRequest> request = createRequest(listObjectsRequest.getBucketName(), null, listObjectsRequest, HttpMethodName.GET);
        if (listObjectsRequest.getPrefix() != null) request.addParameter("prefix", listObjectsRequest.getPrefix());
        if (listObjectsRequest.getMarker() != null) request.addParameter("marker", listObjectsRequest.getMarker());
        if (listObjectsRequest.getDelimiter() != null) request.addParameter("delimiter", listObjectsRequest.getDelimiter());
        if (listObjectsRequest.getMaxKeys() != null && listObjectsRequest.getMaxKeys().intValue() >= 0) request.addParameter("max-keys", listObjectsRequest.getMaxKeys().toString());
        if (listObjectsRequest.getEncodingType() != null) request.addParameter("encoding-type", listObjectsRequest.getEncodingType());

        return invoke(request, new Unmarshallers.ListObjectsUnmarshaller(), listObjectsRequest.getBucketName(), null);
    }

    /* (non-Javadoc)
     * @see com.amazonaws.services.s3.AmazonS3#listNextBatchOfObjects(com.amazonaws.services.s3.model.S3ObjectListing)
     */
    public ObjectListing listNextBatchOfObjects(ObjectListing previousObjectListing)
            throws AmazonClientException, AmazonServiceException {
        assertParameterNotNull(previousObjectListing,
                "The previous object listing parameter must be specified when listing the next batch of objects in a bucket");

        if (!previousObjectListing.isTruncated()) {
            ObjectListing emptyListing = new ObjectListing();
            emptyListing.setBucketName(previousObjectListing.getBucketName());
            emptyListing.setDelimiter(previousObjectListing.getDelimiter());
            emptyListing.setMarker(previousObjectListing.getNextMarker());
            emptyListing.setMaxKeys(previousObjectListing.getMaxKeys());
            emptyListing.setPrefix(previousObjectListing.getPrefix());
            emptyListing.setEncodingType(previousObjectListing.getEncodingType());
            emptyListing.setTruncated(false);

            return emptyListing;
        }
        return listObjects(new ListObjectsRequest(
                previousObjectListing.getBucketName(),
                previousObjectListing.getPrefix(),
                previousObjectListing.getNextMarker(),
                previousObjectListing.getDelimiter(),
                Integer.valueOf(previousObjectListing.getMaxKeys()))
                .withEncodingType(previousObjectListing.getEncodingType()));
    }


    /* (non-Javadoc)
     * @see com.amazonaws.services.s3.AmazonS3#getS3AccountOwner()
     */
    public Owner getS3AccountOwner()
            throws AmazonClientException, AmazonServiceException {
        Request<ListBucketsRequest> request = createRequest(null, null, new ListBucketsRequest(), HttpMethodName.GET);
        return invoke(request, new Unmarshallers.ListBucketsOwnerUnmarshaller(), null, null);
    }

    /* (non-Javadoc)
     * @see com.amazonaws.services.s3.AmazonS3#listBuckets()
     */
    public List<Bucket> listBuckets(ListBucketsRequest listBucketsRequest)
            throws AmazonClientException, AmazonServiceException {
        Request<ListBucketsRequest> request = createRequest(null, null, listBucketsRequest, HttpMethodName.GET);
        return invoke(request, new Unmarshallers.ListBucketsUnmarshaller(), null, null);
    }

    /* (non-Javadoc)
     * @see com.amazonaws.services.s3.AmazonS3#listBuckets()
     */
    public List<Bucket> listBuckets()
            throws AmazonClientException, AmazonServiceException {
        return listBuckets(new ListBucketsRequest());
    }

    /* (non-Javadoc)
     * @see com.amazonaws.services.s3.AmazonS3#createBucketjava.lang.String)
     */
    public Bucket createBucket(String bucketName)
            throws AmazonClientException, AmazonServiceException {
        return createBucket(new CreateBucketRequest(bucketName));
    }

    /* (non-Javadoc)
     * @see com.amazonaws.services.s3.AmazonS3#createBucket(java.lang.String, com.amazonaws.services.s3.model.Region)
     */
    public Bucket createBucket(String bucketName, Region region)
            throws AmazonClientException, AmazonServiceException {
        return createBucket(new CreateBucketRequest(bucketName, region));
    }

    /* (non-Javadoc)
     * @see com.amazonaws.services.s3.AmazonS3#createBucket(java.lang.String, java.lang.String)
     */
    public Bucket createBucket(String bucketName, String region)
            throws AmazonClientException, AmazonServiceException {
        return createBucket(new CreateBucketRequest(bucketName, region));
    }

    /* (non-Javadoc)
     * @see com.amazonaws.services.s3.AmazonS3#createBucket(com.amazonaws.services.s3.model.CreateBucketRequest)
     */
    public Bucket createBucket(CreateBucketRequest createBucketRequest)
            throws AmazonClientException, AmazonServiceException {
        assertParameterNotNull(createBucketRequest,
                "The CreateBucketRequest parameter must be specified when creating a bucket");

        String bucketName = createBucketRequest.getBucketName();
        String region = createBucketRequest.getRegion();
        assertParameterNotNull(bucketName,
                "The bucket name parameter must be specified when creating a bucket");

        if (bucketName != null) bucketName = bucketName.trim();
        BucketNameUtils.validateBucketName(bucketName);

        Request<CreateBucketRequest> request = createRequest(bucketName, null, createBucketRequest, HttpMethodName.PUT);

        if ( createBucketRequest.getAccessControlList() != null ) {
            addAclHeaders(request, createBucketRequest.getAccessControlList());
        } else if ( createBucketRequest.getCannedAcl() != null ) {
            request.addHeader(Headers.S3_CANNED_ACL, createBucketRequest.getCannedAcl().toString());
        }

        /*
         * If we're talking to a region-specific endpoint other than the US, we
         * *must* specify a location constraint. Try to derive the region from
         * the endpoint.
         */
        if (!(this.endpoint.getHost().equals(Constants.S3_HOSTNAME))
                && (region == null || region.isEmpty())) {

            try {
                region = RegionUtils
                    .getRegionByEndpoint(this.endpoint.getHost())
                    .getName();
            } catch (IllegalArgumentException exception) {
                // Endpoint does not correspond to a known region; send the
                // request with no location constraint and hope for the best.
            }

        }

        /*
         * We can only send the CreateBucketConfiguration if we're *not*
         * creating a bucket in the US region.
         */
        if (region != null && !region.toUpperCase().equals(Region.US_Standard.toString())) {
            XmlWriter xml = new XmlWriter();
            xml.start("CreateBucketConfiguration", "xmlns", Constants.XML_NAMESPACE);
            xml.start("LocationConstraint").value(region).end();
            xml.end();

            request.setContent(new ByteArrayInputStream(xml.getBytes()));
        }

        invoke(request, voidResponseHandler, bucketName, null);

        return new Bucket(bucketName);
    }


    /**
     * {@inheritDoc}
     * @see #getBucketAcl(String)
     */
    @Override
    public AccessControlList getBucketAcl(String bucketName)
            throws AmazonClientException, AmazonServiceException {
        assertParameterNotNull(bucketName,
                "The bucket name parameter must be specified when requesting a bucket's ACL");
        return getAcl(bucketName, null, null, null);
    }

    /* (non-Javadoc)
     * @see com.amazonaws.services.s3.AmazonS3#getBucketAcl(com.amazonaws.services.s3.GetBucketAclRequest)
     */
    public AccessControlList getBucketAcl(GetBucketAclRequest getBucketAclRequest)
        throws AmazonClientException, AmazonServiceException {
        String bucketName = getBucketAclRequest.getBucketName();
        assertParameterNotNull(bucketName, "The bucket name parameter must be specified when requesting a bucket's ACL");

        return getAcl(bucketName, null, null, getBucketAclRequest);
    }

    @Override
    public void setBucketAcl(SetBucketAclRequest setBucketAclRequest)
            throws AmazonClientException, AmazonServiceException {
        String bucketName = setBucketAclRequest.getBucketName();
        CannedAccessControlList cannedAcl = setBucketAclRequest.getCannedAcl();
        assertParameterNotNull(bucketName, "The bucket name parameter must be specified when setting a bucket's ACL");

        if (cannedAcl != null) {
            setAcl(bucketName, null, null, cannedAcl, setBucketAclRequest);
        } else {
            assertParameterNotNull(null, "The ACL parameter must be specified when setting a bucket's ACL");
        }
    }

    @Override
    public void setBucketAcl(String bucketName, CannedAccessControlList acl)
            throws AmazonClientException, AmazonServiceException {
        setBucketAcl0(bucketName, acl, null);
    }

    /**
     * Same as {@link #setBucketAcl(String, CannedAccessControlList)}
     * but allows specifying a request metric collector.
     */
    public void setBucketAcl(String bucketName, CannedAccessControlList acl,
            RequestMetricCollector requestMetricCollector) throws AmazonClientException,
            AmazonServiceException {
        setBucketAcl0(bucketName, acl, requestMetricCollector);
    }

    private void setBucketAcl0(String bucketName, CannedAccessControlList acl,
            RequestMetricCollector col) throws AmazonClientException,
            AmazonServiceException {
        assertParameterNotNull(bucketName, "The bucket name parameter must be specified when setting a bucket's ACL");
        assertParameterNotNull(acl, "The ACL parameter must be specified when setting a bucket's ACL");

        setAcl(bucketName, null, null, acl,
            new GenericBucketRequest(bucketName)
                .withRequestMetricCollector(col));
    }

    /* (non-Javadoc)
     * @see com.amazonaws.services.s3.AmazonS3#getObjectMetadata(java.lang.String, java.lang.String)
     */
    public ObjectMetadata getObjectMetadata(String bucketName, String key)
            throws AmazonClientException, AmazonServiceException {
        return getObjectMetadata(new GetObjectMetadataRequest(bucketName, key));
    }

    /* (non-Javadoc)
     * @see com.amazonaws.services.s3.AmazonS3#getObjectMetadata(com.amazonaws.services.s3.model.GetObjectMetadataRequest)
     */
    public ObjectMetadata getObjectMetadata(GetObjectMetadataRequest getObjectMetadataRequest)
            throws AmazonClientException, AmazonServiceException {
        assertParameterNotNull(getObjectMetadataRequest, "The GetObjectMetadataRequest parameter must be specified when requesting an object's metadata");

        String bucketName = getObjectMetadataRequest.getBucketName();
        String key = getObjectMetadataRequest.getKey();
        String versionId = getObjectMetadataRequest.getVersionId();

        assertParameterNotNull(bucketName, "The bucket name parameter must be specified when requesting an object's metadata");
        assertParameterNotNull(key, "The key parameter must be specified when requesting an object's metadata");

        Request<GetObjectMetadataRequest> request = createRequest(bucketName, key, getObjectMetadataRequest, HttpMethodName.HEAD);
        if (versionId != null) request.addParameter("versionId", versionId);

        populateSseCpkRequestParameters(request, getObjectMetadataRequest.getSSECustomerKey());

        return invoke(request, new S3MetadataResponseHandler(), bucketName, key);
    }

    /* (non-Javadoc)
     * @see com.amazonaws.services.s3.AmazonS3#getObject(java.lang.String, java.lang.String)
     */
    public S3Object getObject(String bucketName, String key)
            throws AmazonClientException, AmazonServiceException {
        return getObject(new GetObjectRequest(bucketName, key));
    }

    /* (non-Javadoc)
     * @see com.amazonaws.services.s3.AmazonS3#doesBucketExist(java.lang.String)
     */
    public boolean doesBucketExist(String bucketName)
            throws AmazonClientException, AmazonServiceException {

        try {
            headBucket(new HeadBucketRequest(bucketName));
            return true;
        } catch (AmazonServiceException ase) {
            // A redirect error or a forbidden error means the bucket exists. So
            // returning true.
            if ((ase.getStatusCode() == Constants.BUCKET_REDIRECT_STATUS_CODE)
                    || (ase.getStatusCode() == Constants.BUCKET_ACCESS_FORBIDDEN_STATUS_CODE)) {
                return true;
            }
            if (ase.getStatusCode() == Constants.NO_SUCH_BUCKET_STATUS_CODE) {
                return false;
            }
            throw ase;

        }
    }


    /**
     * Performs a head bucket operation on the requested bucket name. This is
     * done to check if the bucket exists and the user has permissions to access
     * it.
     *
     * @param headBucketRequest The request containing the bucket name.
     * @throws AmazonClientException
     * @throws AmazonServiceException
     */
    private void headBucket(HeadBucketRequest headBucketRequest)
            throws AmazonClientException, AmazonServiceException {

        String bucketName = headBucketRequest.getBucketName();

        assertParameterNotNull(bucketName,
                "The bucketName parameter must be specified.");

        Request<HeadBucketRequest> request = createRequest(bucketName, null,
                headBucketRequest, HttpMethodName.HEAD);
        invoke(request, voidResponseHandler, bucketName, null);
    }

    /* (non-Javadoc)
     * @see com.amazonaws.services.s3.AmazonS3#getObject(com.amazonaws.services.s3.model.GetObjectRequest)
     */
    public S3Object getObject(GetObjectRequest getObjectRequest)
            throws AmazonClientException, AmazonServiceException {
        assertParameterNotNull(getObjectRequest,
                "The GetObjectRequest parameter must be specified when requesting an object");
        assertParameterNotNull(getObjectRequest.getBucketName(),
                "The bucket name parameter must be specified when requesting an object");
        assertParameterNotNull(getObjectRequest.getKey(),
                "The key parameter must be specified when requesting an object");

        Request<GetObjectRequest> request = createRequest(getObjectRequest.getBucketName(), getObjectRequest.getKey(), getObjectRequest, HttpMethodName.GET);

        if (getObjectRequest.getVersionId() != null) {
            request.addParameter("versionId", getObjectRequest.getVersionId());
        }

        // Range
        long[] range = getObjectRequest.getRange();
        if (range != null) {
            request.addHeader(Headers.RANGE, "bytes=" + Long.toString(range[0]) + "-" + Long.toString(range[1]));
        }

        addResponseHeaderParameters(request, getObjectRequest.getResponseHeaders());

        addDateHeader(request, Headers.GET_OBJECT_IF_MODIFIED_SINCE,
                getObjectRequest.getModifiedSinceConstraint());
        addDateHeader(request, Headers.GET_OBJECT_IF_UNMODIFIED_SINCE,
                getObjectRequest.getUnmodifiedSinceConstraint());
        addStringListHeader(request, Headers.GET_OBJECT_IF_MATCH,
                getObjectRequest.getMatchingETagConstraints());
        addStringListHeader(request, Headers.GET_OBJECT_IF_NONE_MATCH,
                getObjectRequest.getNonmatchingETagConstraints());

        // Populate the SSE-CPK parameters to the request header
        populateSseCpkRequestParameters(request, getObjectRequest.getSSECustomerKey());
        final ProgressListener listener = getObjectRequest.getGeneralProgressListener();
        publishProgress(listener, ProgressEventType.TRANSFER_STARTED_EVENT);

        try {
            S3Object s3Object = invoke(request, new S3ObjectResponseHandler(),
                    getObjectRequest.getBucketName(), getObjectRequest.getKey());
            /*
             * TODO: For now, it's easiest to set there here in the client, but
             *       we could push this back into the response handler with a
             *       little more work.
             */
            s3Object.setBucketName(getObjectRequest.getBucketName());
            s3Object.setKey(getObjectRequest.getKey());
            InputStream is = s3Object.getObjectContent();
            HttpRequestBase httpRequest = s3Object.getObjectContent().getHttpRequest();
            // Hold a reference to this client while the InputStream is still
            // around - otherwise a finalizer in the HttpClient may reset the
            // underlying TCP connection out from under us.
            is = new ServiceClientHolderInputStream(is, this);
            // used trigger a tranfer complete event when the stream is entirely consumed
            ProgressInputStream progressInputStream =
                new ProgressInputStream(is, listener) {
                @Override protected void onEOF() {
                    publishProgress(getListener(), ProgressEventType.TRANSFER_COMPLETED_EVENT);
                }
            };
            is = progressInputStream;

            // The Etag header contains a server-side MD5 of the object. If
            // we're downloading the whole object, by default we wrap the
            // stream in a validator that calculates an MD5 of the downloaded
            // bytes and complains if what we received doesn't match the Etag.
            if ( !skipContentMd5IntegrityCheck(getObjectRequest) ) {
                byte[] serverSideHash = null;
                String etag = s3Object.getObjectMetadata().getETag();
                if (etag != null && ServiceUtils.isMultipartUploadETag(etag) == false) {
                    serverSideHash = BinaryUtils.fromHex(s3Object.getObjectMetadata().getETag());
                    try {
                        // No content length check is performed when the
                        // MD5 check is enabled, since a correct MD5 check would
                        // imply a correct content length.
                        MessageDigest digest = MessageDigest.getInstance("MD5");
                        is = new DigestValidationInputStream(is, digest, serverSideHash);
                    } catch (NoSuchAlgorithmException e) {
                        log.warn("No MD5 digest algorithm available.  Unable to calculate "
                                    + "checksum and verify data integrity.", e);
                    }
                }
            } else {
                // Ensures the data received from S3 has the same length as the
                // expected content-length
                is = new LengthCheckInputStream(is,
                    s3Object.getObjectMetadata().getContentLength(), // expected length
                    INCLUDE_SKIPPED_BYTES); // bytes received from S3 are all included even if skipped
            }

            // Re-wrap within an S3ObjectInputStream. Explicitly do not collect
            // metrics here because we know we're ultimately wrapping another
            // S3ObjectInputStream which will take care of that.
            s3Object.setObjectContent(new S3ObjectInputStream(is, httpRequest, false));

            return s3Object;
        } catch (AmazonS3Exception ase) {
            /*
             * If the request failed because one of the specified constraints
             * was not met (ex: matching ETag, modified since date, etc.), then
             * return null, so that users don't have to wrap their code in
             * try/catch blocks and check for this status code if they want to
             * use constraints.
             */
            if (ase.getStatusCode() == 412 || ase.getStatusCode() == 304) {
                publishProgress(listener, ProgressEventType.TRANSFER_CANCELED_EVENT);
                return null;
            }
            publishProgress(listener, ProgressEventType.TRANSFER_FAILED_EVENT);
            throw ase;
        }
    }

    /* (non-Javadoc)
     * @see com.amazonaws.services.s3.AmazonS3#getObject(com.amazonaws.services.s3.model.GetObjectRequest, java.io.File)
     */
    public ObjectMetadata getObject(final GetObjectRequest getObjectRequest, File destinationFile)
            throws AmazonClientException, AmazonServiceException {
        assertParameterNotNull(destinationFile,
                "The destination file parameter must be specified when downloading an object directly to a file");

        S3Object s3Object = ServiceUtils.retryableDownloadS3ObjectToFile(destinationFile, new ServiceUtils.RetryableS3DownloadTask() {

            @Override
            public S3Object getS3ObjectStream() {
                return getObject(getObjectRequest);
            }

            @Override
            public boolean needIntegrityCheck() {
                return !skipContentMd5IntegrityCheck(getObjectRequest);
            }

        }, ServiceUtils.OVERWRITE_MODE);
        // getObject can return null if constraints were specified but not met
        if (s3Object == null) return null;

        return s3Object.getObjectMetadata();
    }

    /**
     * Returns whether the specified request should skip MD5 check on the
     * requested object content.
     */
    private static boolean skipContentMd5IntegrityCheck(AmazonWebServiceRequest request) {
        if (request instanceof GetObjectRequest) {
            if (System.getProperty("com.amazonaws.services.s3.disableGetObjectMD5Validation") != null)
                return true;

            GetObjectRequest getObjectRequest = (GetObjectRequest)request;
            // Skip MD5 check for range get
            if (getObjectRequest.getRange() != null)
                return true;
            if (getObjectRequest.getSSECustomerKey() != null)
                return true;
        } else if (request instanceof PutObjectRequest) {
            PutObjectRequest putObjectRequest = (PutObjectRequest)request;
            return putObjectRequest.getSSECustomerKey() != null;
        } else if (request instanceof UploadPartRequest) {
            UploadPartRequest uploadPartRequest = (UploadPartRequest)request;
            return uploadPartRequest.getSSECustomerKey() != null;
        }
        return false;
    }

    /* (non-Javadoc)
     * @see com.amazonaws.services.s3.AmazonS3#deleteBucket(java.lang.String)
     */
    public void deleteBucket(String bucketName)
            throws AmazonClientException, AmazonServiceException {
        deleteBucket(new DeleteBucketRequest(bucketName));
    }

    /* (non-Javadoc)
     * @see com.amazonaws.services.s3.AmazonS3#deleteBucket(com.amazonaws.services.s3.model.DeleteBucketRequest)
     */
    public void deleteBucket(DeleteBucketRequest deleteBucketRequest)
            throws AmazonClientException, AmazonServiceException {
        assertParameterNotNull(deleteBucketRequest,
                "The DeleteBucketRequest parameter must be specified when deleting a bucket");

        String bucketName = deleteBucketRequest.getBucketName();
        assertParameterNotNull(bucketName,
                "The bucket name parameter must be specified when deleting a bucket");

        Request<DeleteBucketRequest> request = createRequest(bucketName, null, deleteBucketRequest, HttpMethodName.DELETE);
        invoke(request, voidResponseHandler, bucketName, null);
    }

    /* (non-Javadoc)
     * @see com.amazonaws.services.s3.AmazonS3#deleteBucketForce(java.lang.String)
     */
    public void deleteBucketForce(String bucketName)
            throws AmazonClientException, AmazonServiceException {
        deleteBucketForce(new DeleteBucketRequest(bucketName));
    }

    /* (non-Javadoc)
     * @see com.amazonaws.services.s3.AmazonS3#deleteBucketForce(com.amazonaws.services.s3.model.DeleteBucketRequest)
     */
    public void deleteBucketForce(DeleteBucketRequest deleteBucketRequest)
            throws AmazonClientException, AmazonServiceException {
        assertParameterNotNull(deleteBucketRequest,
                "The DeleteBucketRequest parameter must be specified when deleting a bucket");

        String bucketName = deleteBucketRequest.getBucketName();
        assertParameterNotNull(bucketName,
                "The bucket name parameter must be specified when deleting a bucket");

        Request<DeleteBucketRequest> request = createRequest(bucketName, null, deleteBucketRequest, HttpMethodName.DELETE);
        request.addParameter("force", null);
        invoke(request, voidResponseHandler, bucketName, null);
    }

    /* (non-Javadoc)
     * @see com.amazonaws.services.s3.AmazonS3#putObject(java.lang.String, java.lang.String, java.io.File)
     */
    public PutObjectResult putObject(String bucketName, String key, File file)
            throws AmazonClientException, AmazonServiceException {
        return putObject(new PutObjectRequest(bucketName, key, file)
            .withMetadata(new ObjectMetadata()));
    }

    /* (non-Javadoc)
     * @see com.amazonaws.services.s3.AmazonS3#putObject(java.lang.String, java.lang.String, java.io.InputStream, com.amazonaws.services.s3.model.S3ObjectMetadata)
     */
    public PutObjectResult putObject(String bucketName, String key, InputStream input, ObjectMetadata metadata)
            throws AmazonClientException, AmazonServiceException {
        return putObject(new PutObjectRequest(bucketName, key, input, metadata));
    }

    @Override
    public PutObjectResult putObject(PutObjectRequest putObjectRequest)
            throws AmazonClientException, AmazonServiceException {
        final File file = putObjectRequest.getFile();
        final InputStream isOrig = putObjectRequest.getInputStream();
        assertParameterNotNull(putObjectRequest, "The PutObjectRequest parameter must be specified when uploading an object");
        final String bucketName = putObjectRequest.getBucketName();
        final String key = putObjectRequest.getKey();
        ObjectMetadata metadata = putObjectRequest.getMetadata();
        InputStream input = isOrig;
        if (metadata == null)
            metadata = new ObjectMetadata();
        assertParameterNotNull(bucketName, "The bucket name parameter must be specified when uploading an object");
        assertParameterNotNull(key, "The key parameter must be specified when uploading an object");
        final boolean skipContentMd5Check = skipContentMd5IntegrityCheck(putObjectRequest);
        // If a file is specified for upload, we need to pull some additional
        // information from it to auto-configure a few options
        if (file != null) {
            // Always set the content length, even if it's already set
            metadata.setContentLength(file.length());
            final boolean calculateMD5 = metadata.getContentMD5() == null;
            // Only set the content type if it hasn't already been set
            if (metadata.getContentType() == null) {
                metadata.setContentType(Mimetypes.getInstance().getMimetype(file));
            }

            if (calculateMD5 && !skipContentMd5Check) {
                try {
                    String contentMd5_b64 = Md5Utils.md5AsBase64(file);
                    metadata.setContentMD5(contentMd5_b64);
                } catch (Exception e) {
                    throw new AmazonClientException(
                            "Unable to calculate MD5 hash: " + e.getMessage(), e);
                }
            }
            input = newResettableInputStream(file, "Unable to find file to upload");
        }
        final ProgressListener listener;
        final ObjectMetadata returnedMetadata;
        MD5DigestCalculatingInputStream md5DigestStream = null;
        try {
            Request<PutObjectRequest> request = createRequest(bucketName, key, putObjectRequest, HttpMethodName.PUT);
            // Make backward compatible with buffer size via system property
            final Integer bufsize = Constants.getS3StreamBufferSize();
            if (bufsize != null) {
                AmazonWebServiceRequest awsreq = request.getOriginalRequest();
                // Note awsreq is never null at this point even if the original
                // request was
                awsreq.getRequestClientOptions()
                    .setReadLimit(bufsize.intValue());
            }
            if ( putObjectRequest.getAccessControlList() != null) {
                addAclHeaders(request, putObjectRequest.getAccessControlList());
            } else if ( putObjectRequest.getCannedAcl() != null ) {
                request.addHeader(Headers.S3_CANNED_ACL, putObjectRequest.getCannedAcl().toString());
            }

            if (putObjectRequest.getStorageClass() != null) {
                request.addHeader(Headers.STORAGE_CLASS, putObjectRequest.getStorageClass());
            }

            // Populate the SSE-CPK parameters to the request header
            populateSseCpkRequestParameters(request, putObjectRequest.getSSECustomerKey());

            // Use internal interface to differentiate 0 from unset.
            final Long contentLength = (Long)metadata.getRawMetadataValue(Headers.CONTENT_LENGTH);
            if (contentLength == null) {
                /*
                 * There's nothing we can do except for let the HTTP client buffer
                 * the input stream contents if the caller doesn't tell us how much
                 * data to expect in a stream since we have to explicitly tell
                 * Amazon S3 how much we're sending before we start sending any of
                 * it.
                 */
                log.warn("No content length specified for stream data.  " +
                         "Stream contents will be buffered in memory and could result in " +
                         "out of memory errors.");
            } else {
                final long expectedLength = contentLength.longValue();
                if (expectedLength >= 0) {
                    // Performs length check on the underlying data stream.
                    // For S3 encryption client, the underlying data stream here
                    // refers to the cipher-text data stream (ie not the underlying
                    // plain-text data stream which in turn may have been wrapped
                    // with it's own length check input stream.)
                    @SuppressWarnings("resource")
                    LengthCheckInputStream lcis = new LengthCheckInputStream(
                        input,
                        expectedLength, // expected data length to be uploaded
                        EXCLUDE_SKIPPED_BYTES);
                    input = lcis;
                }
            }
            if (metadata.getContentMD5() == null
                    && !skipContentMd5Check ) {
                /*
                 * If the user hasn't set the content MD5, then we don't want to
                 * buffer the whole stream in memory just to calculate it. Instead,
                 * we can calculate it on the fly and validate it with the returned
                 * ETag from the object upload.
                 */
                input = md5DigestStream = new MD5DigestCalculatingInputStream(input);
            }

            if (metadata.getContentType() == null) {
                /*
                 * Default to the "application/octet-stream" if the user hasn't
                 * specified a content type.
                 */
                metadata.setContentType(Mimetypes.MIMETYPE_OCTET_STREAM);
            }

            populateRequestMetadata(request, metadata);
            request.setContent(input);
            listener = putObjectRequest.getGeneralProgressListener();
            publishProgress(listener, ProgressEventType.TRANSFER_STARTED_EVENT);
            try {
                returnedMetadata = invoke(request, new S3MetadataResponseHandler(), bucketName, key);
            } catch (Throwable t) {
                publishProgress(listener, ProgressEventType.TRANSFER_FAILED_EVENT);
                throw failure(t);
            }
        } finally {
            cleanupDataSource(putObjectRequest, file, isOrig, input, log);
        }
        String contentMd5 = metadata.getContentMD5();
        if (md5DigestStream != null) {
            contentMd5 = Base64.encodeAsString(md5DigestStream.getMd5Digest());
        }

        final String etag = returnedMetadata.getETag();
        if (contentMd5 != null && !skipContentMd5Check) {
            byte[] clientSideHash = BinaryUtils.fromBase64(contentMd5);
            byte[] serverSideHash = BinaryUtils.fromHex(etag);

            if (!Arrays.equals(clientSideHash, serverSideHash)) {
                publishProgress(listener, ProgressEventType.TRANSFER_FAILED_EVENT);
                throw new AmazonClientException(
                     "Unable to verify integrity of data upload.  "
                    + "Client calculated content hash (contentMD5: "
                    + contentMd5
                    + " in base 64) didn't match hash (etag: "
                    + etag
                    + " in hex) calculated by Amazon S3.  "
                    + "You may need to delete the data stored in Amazon S3. (metadata.contentMD5: "
                    + metadata.getContentMD5()
                    + ", md5DigestStream: " + md5DigestStream
                    + ", bucketName: " + bucketName + ", key: " + key
                    + ")");
            }
        }
        publishProgress(listener, ProgressEventType.TRANSFER_COMPLETED_EVENT);
        PutObjectResult result = new PutObjectResult();
        result.setETag(etag);
        result.setVersionId(returnedMetadata.getVersionId());
        result.setSSEAlgorithm(returnedMetadata.getSSEAlgorithm());
        result.setSSECustomerAlgorithm(returnedMetadata.getSSECustomerAlgorithm());
        result.setSSECustomerKeyMd5(returnedMetadata.getSSECustomerKeyMd5());
        result.setExpirationTime(returnedMetadata.getExpirationTime());
        result.setExpirationTimeRuleId(returnedMetadata.getExpirationTimeRuleId());
        result.setContentMd5(contentMd5);
        return result;
    }

    /**
     * Sets the access control headers for the request given.
     */
    private static void addAclHeaders(Request<? extends AmazonWebServiceRequest> request, AccessControlList acl) {
        Set<Grant> grants = acl.getGrants();
        Map<Permission, Collection<Grantee>> grantsByPermission = new HashMap<Permission, Collection<Grantee>>();
        for ( Grant grant : grants ) {
            if ( !grantsByPermission.containsKey(grant.getPermission()) ) {
                grantsByPermission.put(grant.getPermission(), new LinkedList<Grantee>());
            }
            grantsByPermission.get(grant.getPermission()).add(grant.getGrantee());
        }
        for ( Permission permission : Permission.values() ) {
            if ( grantsByPermission.containsKey(permission) ) {
                Collection<Grantee> grantees = grantsByPermission.get(permission);
                boolean seenOne = false;
                StringBuilder granteeString = new StringBuilder();
                for ( Grantee grantee : grantees ) {
                    if ( !seenOne )
                        seenOne = true;
                    else
                        granteeString.append(", ");
                    granteeString.append(grantee.getTypeIdentifier()).append("=").append("\"")
                            .append(grantee.getIdentifier()).append("\"");
                }
                request.addHeader(permission.getHeaderName(), granteeString.toString());
            }
        }
    }

    @Override
    public CopyObjectResult copyObject(String sourceBucketName, String sourceKey,
                                       String destinationBucketName, String destinationKey)
            throws AmazonClientException, AmazonServiceException {
        return copyObject(new CopyObjectRequest(sourceBucketName, sourceKey,
                                                destinationBucketName, destinationKey));
    }

    @Override
    public CopyObjectResult copyObject(CopyObjectRequest copyObjectRequest)
            throws AmazonClientException, AmazonServiceException {
        assertParameterNotNull(copyObjectRequest.getSourceBucketName(),
                "The source bucket name must be specified when copying an object");
        assertParameterNotNull(copyObjectRequest.getSourceKey(),
                "The source object key must be specified when copying an object");
        assertParameterNotNull(copyObjectRequest.getDestinationBucketName(),
                "The destination bucket name must be specified when copying an object");
        assertParameterNotNull(copyObjectRequest.getDestinationKey(),
                "The destination object key must be specified when copying an object");

        String destinationKey = copyObjectRequest.getDestinationKey();
        String destinationBucketName = copyObjectRequest.getDestinationBucketName();

        Request<CopyObjectRequest> request = createRequest(destinationBucketName, destinationKey, copyObjectRequest, HttpMethodName.PUT);

        populateRequestWithCopyObjectParameters(request, copyObjectRequest);
        /*
         * We can't send a non-zero length Content-Length header if the user
         * specified it, otherwise it messes up the HTTP connection when the
         * remote server thinks there's more data to pull.
         */
        setZeroContentLength(request);
        CopyObjectResultHandler copyObjectResultHandler = null;
        try {
            @SuppressWarnings("unchecked")
            ResponseHeaderHandlerChain<CopyObjectResultHandler> handler = new ResponseHeaderHandlerChain<CopyObjectResultHandler>(
                    // xml payload unmarshaller
                    new Unmarshallers.CopyObjectUnmarshaller(),
                    // header handlers
                    new ServerSideEncryptionHeaderHandler<CopyObjectResultHandler>(),
                    new S3VersionHeaderHandler(),
                    new ObjectExpirationHeaderHandler<CopyObjectResultHandler>());
            copyObjectResultHandler = invoke(request, handler, destinationBucketName, destinationKey);
        } catch (AmazonS3Exception ase) {
            /*
             * If the request failed because one of the specified constraints
             * was not met (ex: matching ETag, modified since date, etc.), then
             * return null, so that users don't have to wrap their code in
             * try/catch blocks and check for this status code if they want to
             * use constraints.
             */
            if (ase.getStatusCode() == Constants.FAILED_PRECONDITION_STATUS_CODE) {
               return null;
            }

            throw ase;
        }

        /*
         * CopyObject has two failure modes:
         *  1 - An HTTP error code is returned and the error is processed like any
         *      other error response.
         *  2 - An HTTP 200 OK code is returned, but the response content contains
         *      an XML error response.
         *
         * This makes it very difficult for the client runtime to cleanly detect
         * this case and handle it like any other error response.  We could
         * extend the runtime to have a more flexible/customizable definition of
         * success/error (per request), but it's probably overkill for this
         * one special case.
         */
        if (copyObjectResultHandler.getErrorCode() != null) {
            String errorCode = copyObjectResultHandler.getErrorCode();
            String errorMessage = copyObjectResultHandler.getErrorMessage();
            String requestId = copyObjectResultHandler.getErrorRequestId();
            String hostId = copyObjectResultHandler.getErrorHostId();

            AmazonS3Exception ase = new AmazonS3Exception(errorMessage);
            ase.setErrorCode(errorCode);
            ase.setErrorType(ErrorType.Service);
            ase.setRequestId(requestId);
            ase.setExtendedRequestId(hostId);
            ase.setServiceName(request.getServiceName());
            ase.setStatusCode(200);

            throw ase;
        }

        // TODO: Might be nice to create this in our custom S3VersionHeaderHandler
        CopyObjectResult copyObjectResult = new CopyObjectResult();
        copyObjectResult.setETag(copyObjectResultHandler.getETag());
        copyObjectResult.setLastModifiedDate(copyObjectResultHandler.getLastModified());
        copyObjectResult.setVersionId(copyObjectResultHandler.getVersionId());
        copyObjectResult.setSSEAlgorithm(copyObjectResultHandler.getSSEAlgorithm());
        copyObjectResult.setSSECustomerAlgorithm(copyObjectResultHandler.getSSECustomerAlgorithm());
        copyObjectResult.setSSECustomerKeyMd5(copyObjectResultHandler.getSSECustomerKeyMd5());
        copyObjectResult.setExpirationTime(copyObjectResultHandler.getExpirationTime());
        copyObjectResult.setExpirationTimeRuleId(copyObjectResultHandler.getExpirationTimeRuleId());

        return copyObjectResult;
    }

    /**
     * Copies a source object to a part of a multipart upload.
     *
     * To copy an object, the caller's account must have read access to the source object and
     * write access to the destination bucket.
     * </p>
     * <p>
     * If constraints are specified in the <code>CopyPartRequest</code>
     * (e.g.
     * {@link CopyPartRequest#setMatchingETagConstraints(List)})
     * and are not satisfied when Amazon S3 receives the
     * request, this method returns <code>null</code>.
     * This method returns a non-null result under all other
     * circumstances.
     * </p>
     *
     * @param copyPartRequest
     *            The request object containing all the options for copying an
     *            Amazon S3 object.
     *
     * @return A {@link CopyPartResult} object containing the information
     *         returned by Amazon S3 about the newly created object, or <code>null</code> if
     *         constraints were specified that weren't met when Amazon S3 attempted
     *         to copy the object.
     *
     * @throws AmazonClientException
     *             If any errors are encountered in the client while making the
     *             request or handling the response.
     * @throws AmazonServiceException
     *             If any errors occurred in Amazon S3 while processing the
     *             request.
     *
     * @see AmazonS3#copyObject(CopyObjectRequest)
     * @see AmazonS3#initiateMultipartUpload(InitiateMultipartUploadRequest)
     */
    public CopyPartResult copyPart(CopyPartRequest copyPartRequest) {
        assertParameterNotNull(copyPartRequest.getSourceBucketName(),
                "The source bucket name must be specified when copying a part");
        assertParameterNotNull(copyPartRequest.getSourceKey(),
                "The source object key must be specified when copying a part");
        assertParameterNotNull(copyPartRequest.getDestinationBucketName(),
                "The destination bucket name must be specified when copying a part");
        assertParameterNotNull(copyPartRequest.getUploadId(),
                "The upload id must be specified when copying a part");
        assertParameterNotNull(copyPartRequest.getDestinationKey(),
                "The destination object key must be specified when copying a part");
        assertParameterNotNull(copyPartRequest.getPartNumber(),
                "The part number must be specified when copying a part");

        String destinationKey = copyPartRequest.getDestinationKey();
        String destinationBucketName = copyPartRequest.getDestinationBucketName();

        Request<CopyPartRequest> request = createRequest(destinationBucketName, destinationKey, copyPartRequest,
                HttpMethodName.PUT);

        populateRequestWithCopyPartParameters(request, copyPartRequest);

        request.addParameter("uploadId", copyPartRequest.getUploadId());
        request.addParameter("partNumber", Integer.toString(copyPartRequest.getPartNumber()));

        /*
         * We can't send a non-zero length Content-Length header if the user
         * specified it, otherwise it messes up the HTTP connection when the
         * remote server thinks there's more data to pull.
         */
        setZeroContentLength(request);
        CopyObjectResultHandler copyObjectResultHandler = null;
        try {
            @SuppressWarnings("unchecked")
            ResponseHeaderHandlerChain<CopyObjectResultHandler> handler = new ResponseHeaderHandlerChain<CopyObjectResultHandler>(
                    // xml payload unmarshaller
                    new Unmarshallers.CopyObjectUnmarshaller(),
                    // header handlers
                    new ServerSideEncryptionHeaderHandler<CopyObjectResultHandler>(),
                    new S3VersionHeaderHandler());
            copyObjectResultHandler = invoke(request, handler, destinationBucketName, destinationKey);
        } catch ( AmazonS3Exception ase ) {
            /*
             * If the request failed because one of the specified constraints
             * was not met (ex: matching ETag, modified since date, etc.), then
             * return null, so that users don't have to wrap their code in
             * try/catch blocks and check for this status code if they want to
             * use constraints.
             */
            if ( ase.getStatusCode() == Constants.FAILED_PRECONDITION_STATUS_CODE ) {
                return null;
            }

            throw ase;
        }

        /*
         * CopyPart has two failure modes: 1 - An HTTP error code is returned
         * and the error is processed like any other error response. 2 - An HTTP
         * 200 OK code is returned, but the response content contains an XML
         * error response.
         *
         * This makes it very difficult for the client runtime to cleanly detect
         * this case and handle it like any other error response. We could
         * extend the runtime to have a more flexible/customizable definition of
         * success/error (per request), but it's probably overkill for this one
         * special case.
         */
        if ( copyObjectResultHandler.getErrorCode() != null ) {
            String errorCode = copyObjectResultHandler.getErrorCode();
            String errorMessage = copyObjectResultHandler.getErrorMessage();
            String requestId = copyObjectResultHandler.getErrorRequestId();
            String hostId = copyObjectResultHandler.getErrorHostId();

            AmazonS3Exception ase = new AmazonS3Exception(errorMessage);
            ase.setErrorCode(errorCode);
            ase.setErrorType(ErrorType.Service);
            ase.setRequestId(requestId);
            ase.setExtendedRequestId(hostId);
            ase.setServiceName(request.getServiceName());
            ase.setStatusCode(200);

            throw ase;
        }

        CopyPartResult copyPartResult = new CopyPartResult();
        copyPartResult.setETag(copyObjectResultHandler.getETag());
        copyPartResult.setPartNumber(copyPartRequest.getPartNumber());
        copyPartResult.setLastModifiedDate(copyObjectResultHandler.getLastModified());
        copyPartResult.setVersionId(copyObjectResultHandler.getVersionId());
        copyPartResult.setSSEAlgorithm(copyObjectResultHandler.getSSEAlgorithm());
        copyPartResult.setSSECustomerAlgorithm(copyObjectResultHandler.getSSECustomerAlgorithm());
        copyPartResult.setSSECustomerKeyMd5(copyObjectResultHandler.getSSECustomerKeyMd5());

        return copyPartResult;
    }

    /* (non-Javadoc)
     * @see com.amazonaws.services.s3.AmazonS3#deleteObject(java.lang.String, java.lang.String)
     */
    public void deleteObject(String bucketName, String key)
            throws AmazonClientException, AmazonServiceException {
        deleteObject(new DeleteObjectRequest(bucketName, key));
    }

    /* (non-Javadoc)
     * @see com.amazonaws.services.s3.AmazonS3#deleteObject(com.amazonaws.services.s3.DeleteObjectRequest)
     */
    public void deleteObject(DeleteObjectRequest deleteObjectRequest)
            throws AmazonClientException, AmazonServiceException {
        assertParameterNotNull(deleteObjectRequest,
            "The delete object request must be specified when deleting an object");

        assertParameterNotNull(deleteObjectRequest.getBucketName(), "The bucket name must be specified when deleting an object");
        assertParameterNotNull(deleteObjectRequest.getKey(), "The key must be specified when deleting an object");

        Request<DeleteObjectRequest> request = createRequest(deleteObjectRequest.getBucketName(), deleteObjectRequest.getKey(), deleteObjectRequest, HttpMethodName.DELETE);
        invoke(request, voidResponseHandler, deleteObjectRequest.getBucketName(), deleteObjectRequest.getKey());
    }

    /* (non-Javadoc)
     * @see com.amazonaws.services.s3.AmazonS3#deleteObjects(com.amazonaws.services.s3.model.DeleteObjectsRequest)
     */
    public DeleteObjectsResult deleteObjects(DeleteObjectsRequest deleteObjectsRequest) {
        Request<DeleteObjectsRequest> request = createRequest(deleteObjectsRequest.getBucketName(), null, deleteObjectsRequest, HttpMethodName.POST);
        request.addParameter("delete", null);

        if ( deleteObjectsRequest.getMfa() != null ) {
            populateRequestWithMfaDetails(request, deleteObjectsRequest.getMfa());
        }

        byte[] content = new MultiObjectDeleteXmlFactory().convertToXmlByteArray(deleteObjectsRequest);
        request.addHeader("Content-Length", String.valueOf(content.length));
        request.addHeader("Content-Type", "application/xml");
        request.setContent(new ByteArrayInputStream(content));
        try {
            byte[] md5 = Md5Utils.computeMD5Hash(content);
            String md5Base64 = BinaryUtils.toBase64(md5);
            request.addHeader("Content-MD5", md5Base64);
        } catch ( Exception e ) {
            throw new AmazonClientException("Couldn't compute md5 sum", e);
        }

        DeleteObjectsResponse response = invoke(request, new Unmarshallers.DeleteObjectsResultUnmarshaller(), deleteObjectsRequest.getBucketName(), null);

        /*
         * If the result was only partially successful, throw an exception
         */
        if ( !response.getErrors().isEmpty() ) {
            throw new MultiObjectDeleteException(response.getErrors(), response.getDeletedObjects());
        }

        return new DeleteObjectsResult(response.getDeletedObjects());
    }

    /* (non-Javadoc)
     * @see com.amazonaws.services.s3.AmazonS3#deleteObjectVersion(java.lang.String, java.lang.String, java.lang.String)
     */
    public void deleteVersion(String bucketName, String key, String versionId)
            throws AmazonClientException, AmazonServiceException {
        deleteVersion(new DeleteVersionRequest(bucketName, key, versionId));
    }

    /* (non-Javadoc)
     * @see com.amazonaws.services.s3.AmazonS3#deleteVersion(com.amazonaws.services.s3.model.DeleteVersionRequest)
     */
    public void deleteVersion(DeleteVersionRequest deleteVersionRequest)
            throws AmazonClientException, AmazonServiceException {
        assertParameterNotNull(deleteVersionRequest,
            "The delete version request object must be specified when deleting a version");

        String bucketName = deleteVersionRequest.getBucketName();
        String key = deleteVersionRequest.getKey();
        String versionId = deleteVersionRequest.getVersionId();

        assertParameterNotNull(bucketName, "The bucket name must be specified when deleting a version");
        assertParameterNotNull(key, "The key must be specified when deleting a version");
        assertParameterNotNull(versionId, "The version ID must be specified when deleting a version");

        Request<DeleteVersionRequest> request = createRequest(bucketName, key, deleteVersionRequest, HttpMethodName.DELETE);
        if (versionId != null) request.addParameter("versionId", versionId);

        if (deleteVersionRequest.getMfa() != null) {
            populateRequestWithMfaDetails(request, deleteVersionRequest.getMfa());
        }

        invoke(request, voidResponseHandler, bucketName, key);
    }

    /* (non-Javadoc)
     * @see com.amazonaws.services.s3.AmazonS3#getBucketVersioningConfiguration(java.lang.String)
     */
    public BucketVersioningConfiguration getBucketVersioningConfiguration(String bucketName)
            throws AmazonClientException, AmazonServiceException {
        assertParameterNotNull(bucketName,
                "The bucket name parameter must be specified when querying versioning configuration");

        Request<GenericBucketRequest> request = createRequest(bucketName, null, new GenericBucketRequest(bucketName), HttpMethodName.GET);
        request.addParameter("versioning", null);

        return invoke(request, new Unmarshallers.BucketVersioningConfigurationUnmarshaller(), bucketName, null);
    }

    /* (non-Javadoc)
     * @see com.amazonaws.services.s3.AmazonS3#getBucketLoggingConfiguration(java.lang.String)
     */
    public BucketLoggingConfiguration getBucketLoggingConfiguration(String bucketName)
            throws AmazonClientException, AmazonServiceException {
        assertParameterNotNull(bucketName,
                "The bucket name parameter must be specified when requesting a bucket's logging status");

        Request<GenericBucketRequest> request = createRequest(bucketName, null, new GenericBucketRequest(bucketName), HttpMethodName.GET);
        request.addParameter("logging", null);

        return invoke(request, new Unmarshallers.BucketLoggingConfigurationnmarshaller(), bucketName, null);
    }

    /* (non-Javadoc)
     * @see com.amazonaws.services.s3.AmazonS3#setBucketLoggingConfiguration(com.amazonaws.services.s3.SetBucketLoggingConfigurationRequest)
     */
    public void setBucketLoggingConfiguration(SetBucketLoggingConfigurationRequest setBucketLoggingConfigurationRequest)
            throws AmazonClientException, AmazonServiceException {
        assertParameterNotNull(setBucketLoggingConfigurationRequest,
            "The set bucket logging configuration request object must be specified when enabling server access logging");

        String bucketName = setBucketLoggingConfigurationRequest.getBucketName();
        BucketLoggingConfiguration loggingConfiguration = setBucketLoggingConfigurationRequest.getLoggingConfiguration();

        assertParameterNotNull(bucketName,
            "The bucket name parameter must be specified when enabling server access logging");
        assertParameterNotNull(loggingConfiguration,
            "The logging configuration parameter must be specified when enabling server access logging");

        Request<SetBucketLoggingConfigurationRequest> request = createRequest(bucketName, null, setBucketLoggingConfigurationRequest, HttpMethodName.PUT);
        request.addParameter("logging", null);

        byte[] bytes = bucketConfigurationXmlFactory.convertToXmlByteArray(loggingConfiguration);
        request.setContent(new ByteArrayInputStream(bytes));

        invoke(request, voidResponseHandler, bucketName, null);
    }

    /* (non-Javadoc)
     * @see com.amazonaws.services.s3.AmazonS3#generatePresignedUrl(java.lang.String, java.lang.String, java.util.Date)
     */
    public URL generatePresignedUrl(String bucketName, String key, Date expiration)
            throws AmazonClientException {
        return generatePresignedUrl(bucketName, key, expiration, HttpMethod.GET);
    }

    /* (non-Javadoc)
     * @see com.amazonaws.services.s3.AmazonS3#generatePresignedUrl(java.lang.String, java.lang.String, java.util.Date, com.amazonaws.HttpMethod)
     */
    public URL generatePresignedUrl(String bucketName, String key, Date expiration, HttpMethod method)
            throws AmazonClientException {
        GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(bucketName, key, method);
        request.setExpiration(expiration);

        return generatePresignedUrl(request);
    }

    /* (non-Javadoc)
     * @see com.amazonaws.services.s3.AmazonS3#generatePresignedUrl(com.amazonaws.services.s3.model.GeneratePresignedUrlRequest)
     */
    public URL generatePresignedUrl(GeneratePresignedUrlRequest generatePresignedUrlRequest)
            throws AmazonClientException {
        assertParameterNotNull(generatePresignedUrlRequest,
            "The request parameter must be specified when generating a pre-signed URL");

        String bucketName = generatePresignedUrlRequest.getBucketName();
        String key = generatePresignedUrlRequest.getKey();

        assertParameterNotNull(bucketName,
            "The bucket name parameter must be specified when generating a pre-signed URL");
        assertParameterNotNull(generatePresignedUrlRequest.getMethod(),
            "The HTTP method request parameter must be specified when generating a pre-signed URL");

        if (generatePresignedUrlRequest.getExpiration() == null) {
            generatePresignedUrlRequest.setExpiration(
                    new Date(System.currentTimeMillis() + 1000 * 60 * 15));
        }

        HttpMethodName httpMethod = HttpMethodName.valueOf(generatePresignedUrlRequest.getMethod().toString());

        // If the key starts with a slash character itself, the following method
        // will actually add another slash before the resource path to prevent
        // the HttpClient mistakenly treating the slash as a path delimiter.
        // For presigned request, we need to remember to remove this extra slash
        // before generating the URL.
        Request<GeneratePresignedUrlRequest> request = createRequest(bucketName, key, generatePresignedUrlRequest, httpMethod);

        for (Entry<String, String> entry : generatePresignedUrlRequest.getRequestParameters().entrySet()) {
            request.addParameter(entry.getKey(), entry.getValue());
        }

        if (generatePresignedUrlRequest.getContentType() != null) {
            request.addHeader(Headers.CONTENT_TYPE, generatePresignedUrlRequest.getContentType());
        }

        if (generatePresignedUrlRequest.getContentMd5() != null) {
            request.addHeader(Headers.CONTENT_MD5, generatePresignedUrlRequest.getContentMd5());
        }

        populateSseCpkRequestParameters(request, generatePresignedUrlRequest.getSSECustomerKey());

        addResponseHeaderParameters(request, generatePresignedUrlRequest.getResponseHeaders());

        Signer signer = createSigner(request, bucketName, key);

        if (signer instanceof Presigner) {
            // If we have a signer which knows how to presign requests,
            // delegate directly to it.
            ((Presigner) signer).presignRequest(
                request,
                awsCredentialsProvider.getCredentials(),
                generatePresignedUrlRequest.getExpiration()
            );
        } else {
            // Otherwise use the default presigning method, which is hardcoded
            // to use QueryStringSigner.
            presignRequest(
                request,
                generatePresignedUrlRequest.getMethod(),
                bucketName,
                key,
                generatePresignedUrlRequest.getExpiration(),
                null
            );
        }

        // Remove the leading slash (if any) in the resource-path
        return ServiceUtils.convertRequestToUrl(request, true);
    }

    /* (non-Javadoc)
     * @see com.amazonaws.services.s3.AmazonS3#abortMultipartUpload(com.amazonaws.services.s3.model.AbortMultipartUploadRequest)
     */
    public void abortMultipartUpload(AbortMultipartUploadRequest abortMultipartUploadRequest)
            throws AmazonClientException, AmazonServiceException {
        assertParameterNotNull(abortMultipartUploadRequest,
            "The request parameter must be specified when aborting a multipart upload");
        assertParameterNotNull(abortMultipartUploadRequest.getBucketName(),
            "The bucket name parameter must be specified when aborting a multipart upload");
        assertParameterNotNull(abortMultipartUploadRequest.getKey(),
            "The key parameter must be specified when aborting a multipart upload");
        assertParameterNotNull(abortMultipartUploadRequest.getUploadId(),
            "The upload ID parameter must be specified when aborting a multipart upload");

        String bucketName = abortMultipartUploadRequest.getBucketName();
        String key = abortMultipartUploadRequest.getKey();

        Request<AbortMultipartUploadRequest> request = createRequest(bucketName, key, abortMultipartUploadRequest, HttpMethodName.DELETE);
        request.addParameter("uploadId", abortMultipartUploadRequest.getUploadId());

        invoke(request, voidResponseHandler, bucketName, key);
    }

    /* (non-Javadoc)
     * @see com.amazonaws.services.s3.AmazonS3#completeMultipartUpload(com.amazonaws.services.s3.model.CompleteMultipartUploadRequest)
     */
    public CompleteMultipartUploadResult completeMultipartUpload(
            CompleteMultipartUploadRequest completeMultipartUploadRequest)
            throws AmazonClientException, AmazonServiceException {
        assertParameterNotNull(completeMultipartUploadRequest,
            "The request parameter must be specified when completing a multipart upload");

        String bucketName = completeMultipartUploadRequest.getBucketName();
        String key = completeMultipartUploadRequest.getKey();
        String uploadId = completeMultipartUploadRequest.getUploadId();
        assertParameterNotNull(bucketName,
            "The bucket name parameter must be specified when completing a multipart upload");
        assertParameterNotNull(key,
            "The key parameter must be specified when completing a multipart upload");
        assertParameterNotNull(uploadId,
            "The upload ID parameter must be specified when completing a multipart upload");
        assertParameterNotNull(completeMultipartUploadRequest.getPartETags(),
            "The part ETags parameter must be specified when completing a multipart upload");

        Request<CompleteMultipartUploadRequest> request = createRequest(bucketName, key, completeMultipartUploadRequest, HttpMethodName.POST);
        request.addParameter("uploadId", uploadId);

        byte[] xml = RequestXmlFactory.convertToXmlByteArray(completeMultipartUploadRequest.getPartETags());
        request.addHeader("Content-Type", "text/plain");
        request.addHeader("Content-Length", String.valueOf(xml.length));

        request.setContent(new ByteArrayInputStream(xml));

        @SuppressWarnings("unchecked")
        ResponseHeaderHandlerChain<CompleteMultipartUploadHandler> responseHandler = new ResponseHeaderHandlerChain<CompleteMultipartUploadHandler>(
                // xml payload unmarshaller
                new Unmarshallers.CompleteMultipartUploadResultUnmarshaller(),
                // header handlers
                new ServerSideEncryptionHeaderHandler<CompleteMultipartUploadHandler>(),
                new ObjectExpirationHeaderHandler<CompleteMultipartUploadHandler>());
        CompleteMultipartUploadHandler handler = invoke(request, responseHandler, bucketName, key);
        if (handler.getCompleteMultipartUploadResult() != null) {
            String versionId = responseHandler.getResponseHeaders().get(Headers.S3_VERSION_ID);
            handler.getCompleteMultipartUploadResult().setVersionId(versionId);
            return handler.getCompleteMultipartUploadResult();
        } else {
            throw handler.getAmazonS3Exception();
        }
    }

    /* (non-Javadoc)
     * @see com.amazonaws.services.s3.AmazonS3#initiateMultipartUpload(com.amazonaws.services.s3.model.InitiateMultipartUploadRequest)
     */
    public InitiateMultipartUploadResult initiateMultipartUpload(
            InitiateMultipartUploadRequest initiateMultipartUploadRequest)
            throws AmazonClientException, AmazonServiceException {
        assertParameterNotNull(initiateMultipartUploadRequest,
            "The request parameter must be specified when initiating a multipart upload");

        assertParameterNotNull(initiateMultipartUploadRequest.getBucketName(),
            "The bucket name parameter must be specified when initiating a multipart upload");
        assertParameterNotNull(initiateMultipartUploadRequest.getKey(),
            "The key parameter must be specified when initiating a multipart upload");

        Request<InitiateMultipartUploadRequest> request = createRequest(initiateMultipartUploadRequest.getBucketName(), initiateMultipartUploadRequest.getKey(), initiateMultipartUploadRequest, HttpMethodName.POST);
        request.addParameter("uploads", null);

        if (initiateMultipartUploadRequest.getStorageClass() != null)
            request.addHeader(Headers.STORAGE_CLASS, initiateMultipartUploadRequest.getStorageClass().toString());

        if ( initiateMultipartUploadRequest.getAccessControlList() != null ) {
            addAclHeaders(request, initiateMultipartUploadRequest.getAccessControlList());
        } else if ( initiateMultipartUploadRequest.getCannedACL() != null ) {
            request.addHeader(Headers.S3_CANNED_ACL, initiateMultipartUploadRequest.getCannedACL().toString());
        }

        if (initiateMultipartUploadRequest.objectMetadata != null) {
            populateRequestMetadata(request, initiateMultipartUploadRequest.objectMetadata);
        }

        // Populate the SSE-CPK parameters to the request header
        populateSseCpkRequestParameters(request, initiateMultipartUploadRequest.getSSECustomerKey());

        // Be careful that we don't send the object's total size as the content
        // length for the InitiateMultipartUpload request.
        setZeroContentLength(request);
        // Set the request content to be empty (but not null) to force the runtime to pass
        // any query params in the query string and not the request body, to keep S3 happy.
        request.setContent(new ByteArrayInputStream(new byte[0]));

        @SuppressWarnings("unchecked")
        ResponseHeaderHandlerChain<InitiateMultipartUploadResult> responseHandler = new ResponseHeaderHandlerChain<InitiateMultipartUploadResult>(
                // xml payload unmarshaller
                new Unmarshallers.InitiateMultipartUploadResultUnmarshaller(),
                // header handlers
                new ServerSideEncryptionHeaderHandler<InitiateMultipartUploadResult>());
        return invoke(request, responseHandler,
                initiateMultipartUploadRequest.getBucketName(), initiateMultipartUploadRequest.getKey());
    }

    /* (non-Javadoc)
     * @see com.amazonaws.services.s3.AmazonS3#listParts(com.amazonaws.services.s3.model.ListPartsRequest)
     */
    public PartListing listParts(ListPartsRequest listPartsRequest)
            throws AmazonClientException, AmazonServiceException {
        assertParameterNotNull(listPartsRequest,
            "The request parameter must be specified when listing parts");

        assertParameterNotNull(listPartsRequest.getBucketName(),
            "The bucket name parameter must be specified when listing parts");
        assertParameterNotNull(listPartsRequest.getKey(),
            "The key parameter must be specified when listing parts");
        assertParameterNotNull(listPartsRequest.getUploadId(),
            "The upload ID parameter must be specified when listing parts");

        Request<ListPartsRequest> request = createRequest(listPartsRequest.getBucketName(), listPartsRequest.getKey(), listPartsRequest, HttpMethodName.GET);
        request.addParameter("uploadId", listPartsRequest.getUploadId());

        if (listPartsRequest.getMaxParts() != null) request.addParameter("max-parts", listPartsRequest.getMaxParts().toString());
        if (listPartsRequest.getPartNumberMarker() != null) request.addParameter("part-number-marker", listPartsRequest.getPartNumberMarker().toString());
        if (listPartsRequest.getEncodingType() != null) request.addParameter("encoding-type", listPartsRequest.getEncodingType());

        return invoke(request, new Unmarshallers.ListPartsResultUnmarshaller(), listPartsRequest.getBucketName(), listPartsRequest.getKey());
    }

    @Override
    public UploadPartResult uploadPart(UploadPartRequest uploadPartRequest)
            throws AmazonClientException, AmazonServiceException {
        assertParameterNotNull(uploadPartRequest,
            "The request parameter must be specified when uploading a part");
        final File fileOrig = uploadPartRequest.getFile();
        final InputStream isOrig = uploadPartRequest.getInputStream();
        final String bucketName = uploadPartRequest.getBucketName();
        final String key        = uploadPartRequest.getKey();
        final String uploadId   = uploadPartRequest.getUploadId();
        final int partNumber    = uploadPartRequest.getPartNumber();
        final long partSize     = uploadPartRequest.getPartSize();
        assertParameterNotNull(bucketName,
            "The bucket name parameter must be specified when uploading a part");
        assertParameterNotNull(key,
            "The key parameter must be specified when uploading a part");
        assertParameterNotNull(uploadId,
            "The upload ID parameter must be specified when uploading a part");
        assertParameterNotNull(partNumber,
            "The part number parameter must be specified when uploading a part");
        assertParameterNotNull(partSize,
            "The part size parameter must be specified when uploading a part");
        Request<UploadPartRequest> request = createRequest(bucketName, key, uploadPartRequest, HttpMethodName.PUT);
        request.addParameter("uploadId", uploadId);
        request.addParameter("partNumber", Integer.toString(partNumber));

        addHeaderIfNotNull(request, Headers.CONTENT_MD5, uploadPartRequest.getMd5Digest());
        request.addHeader(Headers.CONTENT_LENGTH, Long.toString(partSize));

        // Populate the SSE-CPK parameters to the request header
        populateSseCpkRequestParameters(request, uploadPartRequest.getSSECustomerKey());
        InputStream isCurr = isOrig;
        try {
            if (fileOrig == null) {
                if (isOrig == null) {
                    throw new IllegalArgumentException(
                        "A File or InputStream must be specified when uploading part");
                }
                // Make backward compatible with buffer size via system property
                final Integer bufsize = Constants.getS3StreamBufferSize();
                if (bufsize != null) {
                    AmazonWebServiceRequest awsreq = request.getOriginalRequest();
                    // Note awsreq is never null at this point even if the original
                    // request was
                    awsreq.getRequestClientOptions()
                        .setReadLimit(bufsize.intValue());
                }
            } else {
                try {
                    isCurr = new ResettableInputStream(fileOrig);
                } catch(IOException e) {
                    throw new IllegalArgumentException("Failed to open file "
                            + fileOrig, e);
                }
            }
            isCurr = new InputSubstream(isCurr,
                    uploadPartRequest.getFileOffset(),
                    partSize,
                    uploadPartRequest.isLastPart());
            MD5DigestCalculatingInputStream md5DigestStream = null;
            if (uploadPartRequest.getMd5Digest() == null
             && !skipContentMd5IntegrityCheck(uploadPartRequest)) {
                /*
                 * If the user hasn't set the content MD5, then we don't want to
                 * buffer the whole stream in memory just to calculate it. Instead,
                 * we can calculate it on the fly and validate it with the returned
                 * ETag from the object upload.
                 */
                isCurr = md5DigestStream = new MD5DigestCalculatingInputStream(isCurr);
            }
            final ProgressListener listener = uploadPartRequest.getGeneralProgressListener();
            publishProgress(listener, ProgressEventType.TRANSFER_PART_STARTED_EVENT);
            return doUploadPart(bucketName, key, uploadId, partNumber,
                    partSize, request, isCurr, md5DigestStream, listener);
        } finally {
            cleanupDataSource(uploadPartRequest, fileOrig, isOrig, isCurr, log);
        }
    }

    private UploadPartResult doUploadPart(final String bucketName,
            final String key, final String uploadId, final int partNumber,
            final long partSize, Request<UploadPartRequest> request,
            InputStream inputStream,
            MD5DigestCalculatingInputStream md5DigestStream,
            final ProgressListener listener) {
        try {
            request.setContent(inputStream);
            ObjectMetadata metadata = invoke(request, new S3MetadataResponseHandler(), bucketName, key);
            final String etag = metadata.getETag();

            if (md5DigestStream != null) {
                byte[] clientSideHash = md5DigestStream.getMd5Digest();
                byte[] serverSideHash = BinaryUtils.fromHex(etag);


                if (!Arrays.equals(clientSideHash, serverSideHash)) {
                    final String info = "bucketName: " + bucketName + ", key: "
                            + key + ", uploadId: " + uploadId
                            + ", partNumber: " + partNumber + ", partSize: "
                            + partSize;
                    throw new AmazonClientException(
                         "Unable to verify integrity of data upload.  "
                        + "Client calculated content hash (contentMD5: "
                        + Base16.encodeAsString(clientSideHash)
                        + " in hex) didn't match hash (etag: "
                        + etag
                        + " in hex) calculated by Amazon S3.  "
                        + "You may need to delete the data stored in Amazon S3. "
                        + "(" + info + ")");
                }
            }
            publishProgress(listener, ProgressEventType.TRANSFER_PART_COMPLETED_EVENT);
            UploadPartResult result = new UploadPartResult();
            result.setETag(etag);
            result.setPartNumber(partNumber);
            result.setSSEAlgorithm(metadata.getSSEAlgorithm());
            result.setSSECustomerAlgorithm(metadata.getSSECustomerAlgorithm());
            result.setSSECustomerKeyMd5(metadata.getSSECustomerKeyMd5());
            return result;
        } catch (Throwable t) {
            publishProgress(listener, ProgressEventType.TRANSFER_PART_FAILED_EVENT);
            // Leaving this here in case anyone is depending on it, but it's
            // inconsistent with other methods which only generate one of
            // COMPLETED_EVENT_CODE or FAILED_EVENT_CODE.
            publishProgress(listener, ProgressEventType.TRANSFER_PART_COMPLETED_EVENT);
            throw failure(t);
        }
    }

    /* (non-Javadoc)
     * @see com.amazonaws.services.s3.AmazonS3#getResponseMetadataForRequest(com.amazonaws.AmazonWebServiceRequest)
     */
    public S3ResponseMetadata getCachedResponseMetadata(AmazonWebServiceRequest request) {
        return (S3ResponseMetadata)client.getResponseMetadataForRequest(request);
    }

    /*
     * Private Interface
     */

    /**
     * <p>
     * Asserts that the specified parameter value is not <code>null</code> and if it is,
     * throws an <code>IllegalArgumentException</code> with the specified error message.
     * </p>
     *
     * @param parameterValue
     *            The parameter value being checked.
     * @param errorMessage
     *            The error message to include in the IllegalArgumentException
     *            if the specified parameter is null.
     */
    private void assertParameterNotNull(Object parameterValue, String errorMessage) {
        if (parameterValue == null) throw new IllegalArgumentException(errorMessage);
    }

    /**
     * <p>
     * Gets the Amazon S3 {@link AccessControlList} (ACL) for the specified resource.
     * (bucket if only the bucketName parameter is specified, otherwise the object with the
     * specified key in the bucket).
     * </p>
     *
     * @param bucketName
     *            The name of the bucket whose ACL should be returned if the key
     *            parameter is not specified, otherwise the bucket containing
     *            the specified key.
     * @param key
     *            The object key whose ACL should be retrieve. If not specified,
     *            the bucket's ACL is returned.
     * @param versionId
     *            The version ID of the object version whose ACL is being
     *            retrieved.
     * @param originalRequest
     *            The original, user facing request object.
     *
     * @return The S3 ACL for the specified resource.
     */
    private AccessControlList getAcl(String bucketName, String key, String versionId, AmazonWebServiceRequest originalRequest) {
        if (originalRequest == null) originalRequest = new GenericBucketRequest(bucketName);

        Request<AmazonWebServiceRequest> request = createRequest(bucketName, key, originalRequest, HttpMethodName.GET);
        request.addParameter("acl", null);
        if (versionId != null) request.addParameter("versionId", versionId);

        return invoke(request, new Unmarshallers.AccessControlListUnmarshaller(), bucketName, key);
    }

    /**
     * Sets the Canned ACL for the specified resource in S3. If only bucketName
     * is specified, the canned ACL will be applied to the bucket, otherwise if
     * bucketName and key are specified, the canned ACL will be applied to the
     * object.
     *
     * @param bucketName
     *            The name of the bucket containing the specified key, or if no
     *            key is listed, the bucket whose ACL will be set.
     * @param key
     *            The optional object key within the specified bucket whose ACL
     *            will be set. If not specified, the bucket ACL will be set.
     * @param versionId
     *            The version ID of the object version whose ACL is being set.
     * @param cannedAcl
     *            The canned ACL to apply to the resource.
     * @param originalRequest
     *            The original, user facing request object.
     */
    private void setAcl(String bucketName, String key, String versionId, CannedAccessControlList cannedAcl, AmazonWebServiceRequest originalRequest) {
        if (originalRequest == null) originalRequest = new GenericBucketRequest(bucketName);

        Request<AmazonWebServiceRequest> request = createRequest(bucketName, key, originalRequest, HttpMethodName.PUT);
        request.addParameter("acl", null);
        request.addHeader(Headers.S3_CANNED_ACL, cannedAcl.toString());
        if (versionId != null) request.addParameter("versionId", versionId);

        invoke(request, voidResponseHandler, bucketName, key);
    }

    /**
     * Sets the ACL for the specified resource in S3. If only bucketName is
     * specified, the ACL will be applied to the bucket, otherwise if bucketName
     * and key are specified, the ACL will be applied to the object.
     *
     * @param bucketName
     *            The name of the bucket containing the specified key, or if no
     *            key is listed, the bucket whose ACL will be set.
     * @param key
     *            The optional object key within the specified bucket whose ACL
     *            will be set. If not specified, the bucket ACL will be set.
     * @param versionId
     *            The version ID of the object version whose ACL is being set.
     * @param acl
     *            The ACL to apply to the resource.
     * @param originalRequest
     *            The original, user facing request object.
     */
    private void setAcl(String bucketName, String key, String versionId, AccessControlList acl, AmazonWebServiceRequest originalRequest) {
        if (originalRequest == null) originalRequest = new GenericBucketRequest(bucketName);

        Request<AmazonWebServiceRequest> request = createRequest(bucketName, key, originalRequest, HttpMethodName.PUT);
        request.addParameter("acl", null);
        if (versionId != null) request.addParameter("versionId", versionId);

        byte[] aclAsXml = new AclXmlFactory().convertToXmlByteArray(acl);
        request.addHeader("Content-Type", "text/plain");
        request.addHeader("Content-Length", String.valueOf(aclAsXml.length));
        request.setContent(new ByteArrayInputStream(aclAsXml));

        invoke(request, voidResponseHandler, bucketName, key);
    }

    /**
     * Returns a "complete" S3 specific signer, taking into the S3 bucket, key,
     * and the current S3 client configuration into account.
     */
    protected Signer createSigner(final Request<?> request,
                                  final String bucketName,
                                  final String key) {

        Signer signer = getSigner();

        if (upgradeToSigV4() && !(signer instanceof AWSS3V4Signer)){

            AWSS3V4Signer v4Signer = new AWSS3V4Signer();

            // Always set the service name; if the user has overridden it via
            // setEndpoint(String, String, String), this will return the right
            // value. Otherwise it will return "s3", which is an appropriate
            // default.
            v4Signer.setServiceName(getServiceNameIntern());

            // If the user has set an authentication region override, pass it
            // to the signer. Otherwise leave it null - the signer will parse
            // region from the request endpoint.

            String regionOverride = getSignerRegionOverride();
            if (regionOverride == null) {
                if (!hasExplicitRegion) {
                    throw new AmazonClientException(
                        "Signature Version 4 requires knowing the region of "
                        + "the bucket you're trying to access. You can "
                        + "configure a region by calling AmazonS3Client."
                        + "setRegion(Region) or AmazonS3Client.setEndpoint("
                        + "String) with a region-specific endpoint such as "
                        + "\"s3-us-west-2.amazonaws.com\".");
                }
            } else {
                v4Signer.setRegionName(regionOverride);
            }

            return v4Signer;

        }

        if (signer instanceof S3Signer) {

            // The old S3Signer needs a method and path passed to its
            // constructor; if that's what we should use, getSigner()
            // will return a dummy instance and we need to create a
            // new one with the appropriate values for this request.

            String resourcePath =
                "/" +
                ((bucketName != null) ? bucketName + "/" : "") +
                ((key != null) ? key : "");

            return new S3Signer(request.getHttpMethod().toString(),
                                resourcePath);
        }

        return signer;
    }

    private boolean upgradeToSigV4() {

        // User has said to always use SigV4 - this will fail if the user
        // attempts to read from or write to a non-US-Standard bucket without
        // explicitly setting the region.

        if (System.getProperty(SDKGlobalConfiguration
                .ENFORCE_S3_SIGV4_SYSTEM_PROPERTY) != null) {

            return true;
        }

        // User has said to enable SigV4 if it's safe - this will fall back
        // to SigV2 if the endpoint has not been set to one of the explicit
        // regional endpoints because we can't be sure it will work otherwise.

        if (System.getProperty(SDKGlobalConfiguration
                .ENABLE_S3_SIGV4_SYSTEM_PROPERTY) != null
            && !endpoint.getHost().endsWith(Constants.S3_HOSTNAME)) {

            return true;
        }

        // Go with the default (SigV4 only if we know we're talking to an
        // endpoint that requires SigV4).

        return false;
    }

    /**
     * Pre-signs the specified request, using a signature query-string
     * parameter.
     *
     * @param request
     *            The request to sign.
     * @param methodName
     *            The HTTP method (GET, PUT, DELETE, HEAD) for the specified
     *            request.
     * @param bucketName
     *            The name of the bucket involved in the request. If the request
     *            is not an operation on a bucket this parameter should be null.
     * @param key
     *            The object key involved in the request. If the request is not
     *            an operation on an object, this parameter should be null.
     * @param expiration
     *            The time at which the signed request is no longer valid, and
     *            will stop working.
     * @param subResource
     *            The optional sub-resource being requested as part of the
     *            request (e.g. "location", "acl", "logging", or "torrent").
     */
    protected <T> void presignRequest(Request<T> request, HttpMethod methodName,
            String bucketName, String key, Date expiration, String subResource) {
        // Run any additional request handlers if present
        beforeRequest(request);

        String resourcePath = "/" +
            ((bucketName != null) ? bucketName + "/" : "") +
            ((key != null) ? HttpUtils.urlEncode(key, true) : "") +
            ((subResource != null) ? "?" + subResource : "");

        // Make sure the resource-path for signing does not contain
        // any consecutive "/"s.
        // Note that we should also follow the same rule to escape
        // consecutive "/"s when generating the presigned URL.
        // See ServiceUtils#convertRequestToUrl(...)
        resourcePath = resourcePath.replaceAll("(?<=/)/", "%2F");

        AWSCredentials credentials = awsCredentialsProvider.getCredentials();
        AmazonWebServiceRequest originalRequest = request.getOriginalRequest();
        if (originalRequest != null && originalRequest.getRequestCredentials() != null) {
            credentials = originalRequest.getRequestCredentials();
        }

        new S3QueryStringSigner<T>(methodName.toString(), resourcePath, expiration).sign(request, credentials);

        // The Amazon S3 DevPay token header is a special exception and can be safely moved
        // from the request's headers into the query string to ensure that it travels along
        // with the pre-signed URL when it's sent back to Amazon S3.
        if (request.getHeaders().containsKey(Headers.SECURITY_TOKEN)) {
            String value = request.getHeaders().get(Headers.SECURITY_TOKEN);
            request.addParameter(Headers.SECURITY_TOKEN, value);
            request.getHeaders().remove(Headers.SECURITY_TOKEN);
        }
    }

    private <T> void beforeRequest(Request<T> request) {
        if (requestHandler2s != null) {
            for (RequestHandler2 requestHandler2 : requestHandler2s) {
                requestHandler2.beforeRequest(request);
            }
        }
    }

    /**
     * Converts the current endpoint set for this client into virtual addressing
     * style, by placing the name of the specified bucket before the S3 service
     * endpoint.
     *
     * @param bucketName
     *            The name of the bucket to use in the virtual addressing style
     *            of the returned URI.
     *
     * @return A new URI, creating from the current service endpoint URI and the
     *         specified bucket.
     */
    private URI convertToVirtualHostEndpoint(String bucketName) {
        try {
            return new URI(endpoint.getScheme() + "://" + bucketName + "." + endpoint.getAuthority());
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid bucket name: " + bucketName, e);
        }
    }

    /**
     * <p>
     * Populates the specified request object with the appropriate headers from
     * the {@link ObjectMetadata} object.
     * </p>
     *
     * @param request
     *            The request to populate with headers.
     * @param metadata
     *            The metadata containing the header information to include in
     *            the request.
     */
    protected static void populateRequestMetadata(Request<?> request, ObjectMetadata metadata) {
        Map<String, Object> rawMetadata = metadata.getRawMetadata();
        if (rawMetadata != null) {
            for (Entry<String, Object> entry : rawMetadata.entrySet()) {
                request.addHeader(entry.getKey(), entry.getValue().toString());
            }
        }

        Date httpExpiresDate = metadata.getHttpExpiresDate();
        if (httpExpiresDate != null) {
            request.addHeader(Headers.EXPIRES, DateUtils.formatRFC822Date(httpExpiresDate));
        }

        Map<String, String> userMetadata = metadata.getUserMetadata();
        if (userMetadata != null) {
            for (Entry<String, String> entry : userMetadata.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();
                if (key != null) key = key.trim();
                if (value != null) value = value.trim();
                request.addHeader(Headers.S3_USER_METADATA_PREFIX + key, value);
            }
        }
    }

    /**
     * <p>
     * Populates the specified request with the specified Multi-Factor
     * Authentication (MFA) details. This includes the MFA header with device serial
     * number and generated token. Since all requests which include the MFA
     * header must be sent over HTTPS, this operation also configures the request object to
     * use HTTPS instead of HTTP.
     * </p>
     *
     * @param request
     *            The request to populate.
     * @param mfa
     *            The Multi-Factor Authentication information.
     */
    private void populateRequestWithMfaDetails(Request<?> request, MultiFactorAuthentication mfa) {
        if (mfa == null) return;

        String endpoint = request.getEndpoint().toString();
        if (endpoint.startsWith("http://")) {
            String httpsEndpoint = endpoint.replace("http://", "https://");
            request.setEndpoint(URI.create(httpsEndpoint));
            log.info("Overriding current endpoint to use HTTPS " +
                    "as required by S3 for requests containing an MFA header");
        }

        request.addHeader(Headers.S3_MFA,
                mfa.getDeviceSerialNumber() + " " + mfa.getToken());
    }

    /**
     * <p>
     * Populates the specified request with the numerous options available in
     * <code>CopyObjectRequest</code>.
     * </p>
     *
     * @param request
     *            The request to populate with headers to represent all the
     *            options expressed in the <code>CopyObjectRequest</code> object.
     * @param copyObjectRequest
     *            The object containing all the options for copying an object in
     *            Amazon S3.
     */
    private static void populateRequestWithCopyObjectParameters(Request<? extends AmazonWebServiceRequest> request, CopyObjectRequest copyObjectRequest) {
        String copySourceHeader =
             "/" + HttpUtils.urlEncode(copyObjectRequest.getSourceBucketName(), true)
           + "/" + HttpUtils.urlEncode(copyObjectRequest.getSourceKey(), true);
        if (copyObjectRequest.getSourceVersionId() != null) {
            copySourceHeader += "?versionId=" + copyObjectRequest.getSourceVersionId();
        }
        request.addHeader("x-amz-copy-source", copySourceHeader);

        addDateHeader(request, Headers.COPY_SOURCE_IF_MODIFIED_SINCE,
                copyObjectRequest.getModifiedSinceConstraint());
        addDateHeader(request, Headers.COPY_SOURCE_IF_UNMODIFIED_SINCE,
                copyObjectRequest.getUnmodifiedSinceConstraint());

        addStringListHeader(request, Headers.COPY_SOURCE_IF_MATCH,
                copyObjectRequest.getMatchingETagConstraints());
        addStringListHeader(request, Headers.COPY_SOURCE_IF_NO_MATCH,
                copyObjectRequest.getNonmatchingETagConstraints());

        if (copyObjectRequest.getAccessControlList() != null) {
            addAclHeaders(request, copyObjectRequest.getAccessControlList());
        } else if (copyObjectRequest.getCannedAccessControlList() != null) {
            request.addHeader(Headers.S3_CANNED_ACL,
                    copyObjectRequest.getCannedAccessControlList().toString());
        }

        if (copyObjectRequest.getStorageClass() != null) {
            request.addHeader(Headers.STORAGE_CLASS, copyObjectRequest.getStorageClass());
        }

        if (copyObjectRequest.getRedirectLocation() != null) {
            request.addHeader(Headers.REDIRECT_LOCATION, copyObjectRequest.getRedirectLocation());
        }

        ObjectMetadata newObjectMetadata = copyObjectRequest.getNewObjectMetadata();
        if (newObjectMetadata != null) {
            request.addHeader(Headers.METADATA_DIRECTIVE, "REPLACE");
            populateRequestMetadata(request, newObjectMetadata);
        }

        // Populate the SSE-CPK parameters for the destination object
        populateSourceSseCpkRequestParameters(request, copyObjectRequest.getSourceSSECustomerKey());
        populateSseCpkRequestParameters(request, copyObjectRequest.getDestinationSSECustomerKey());
    }

    /**
     * <p>
     * Populates the specified request with the numerous options available in
     * <code>CopyObjectRequest</code>.
     * </p>
     *
     * @param request
     *            The request to populate with headers to represent all the
     *            options expressed in the <code>CopyPartRequest</code> object.
     * @param copyPartRequest
     *            The object containing all the options for copying an object in
     *            Amazon S3.
     */
    private static void populateRequestWithCopyPartParameters(Request<?> request, CopyPartRequest copyPartRequest) {
        String copySourceHeader =
             "/" + HttpUtils.urlEncode(copyPartRequest.getSourceBucketName(), true)
           + "/" + HttpUtils.urlEncode(copyPartRequest.getSourceKey(), true);
        if (copyPartRequest.getSourceVersionId() != null) {
            copySourceHeader += "?versionId=" + copyPartRequest.getSourceVersionId();
        }
        request.addHeader("x-amz-copy-source", copySourceHeader);

        addDateHeader(request, Headers.COPY_SOURCE_IF_MODIFIED_SINCE,
                copyPartRequest.getModifiedSinceConstraint());
        addDateHeader(request, Headers.COPY_SOURCE_IF_UNMODIFIED_SINCE,
                copyPartRequest.getUnmodifiedSinceConstraint());

        addStringListHeader(request, Headers.COPY_SOURCE_IF_MATCH,
                copyPartRequest.getMatchingETagConstraints());
        addStringListHeader(request, Headers.COPY_SOURCE_IF_NO_MATCH,
                copyPartRequest.getNonmatchingETagConstraints());

        if ( copyPartRequest.getFirstByte() != null && copyPartRequest.getLastByte() != null ) {
            String range = "bytes=" + copyPartRequest.getFirstByte() + "-" + copyPartRequest.getLastByte();
            request.addHeader(Headers.COPY_PART_RANGE, range);
        }

        // Populate the SSE-CPK parameters for the destination object
        populateSourceSseCpkRequestParameters(request, copyPartRequest.getSourceSSECustomerKey());
        populateSseCpkRequestParameters(request, copyPartRequest.getDestinationSSECustomerKey());
    }

    /**
     * <p>
     * Populates the specified request with the numerous attributes available in
     * <code>SSEWithCustomerKeyRequest</code>.
     * </p>
     *
     * @param request
     *            The request to populate with headers to represent all the
     *            options expressed in the
     *            <code>ServerSideEncryptionWithCustomerKeyRequest</code>
     *            object.
     * @param sseCpkRequest
     *            The request object for an S3 operation that allows server-side
     *            encryption using customer-provided keys.
     */
    private static void populateSseCpkRequestParameters(Request<?> request, SSECustomerKey sseKey) {
        if (sseKey == null) return;

        addHeaderIfNotNull(request, Headers.SERVER_SIDE_ENCRYPTION_CUSTOMER_ALGORITHM,
                sseKey.getAlgorithm());
        addHeaderIfNotNull(request, Headers.SERVER_SIDE_ENCRYPTION_CUSTOMER_KEY,
                sseKey.getKey());
        addHeaderIfNotNull(request, Headers.SERVER_SIDE_ENCRYPTION_CUSTOMER_KEY_MD5,
                sseKey.getMd5());
        // Calculate the MD5 hash of the encryption key and fill it in the
        // header, if the user didn't specify it in the metadata
        if (sseKey.getKey() != null
                && sseKey.getMd5() == null) {
            String encryptionKey_b64 = sseKey.getKey();
            byte[] encryptionKey = Base64.decode(encryptionKey_b64);
            request.addHeader(Headers.SERVER_SIDE_ENCRYPTION_CUSTOMER_KEY_MD5,
                    Md5Utils.md5AsBase64(encryptionKey));
        }
    }

    private static void populateSourceSseCpkRequestParameters(Request<?> request, SSECustomerKey sseKey) {
        if (sseKey == null) return;

        // Populate the SSE-CPK parameters for the source object
        addHeaderIfNotNull(request, Headers.COPY_SOURCE_SERVER_SIDE_ENCRYPTION_CUSTOMER_ALGORITHM,
                sseKey.getAlgorithm());
        addHeaderIfNotNull(request, Headers.COPY_SOURCE_SERVER_SIDE_ENCRYPTION_CUSTOMER_KEY,
                sseKey.getKey());
        addHeaderIfNotNull(request, Headers.COPY_SOURCE_SERVER_SIDE_ENCRYPTION_CUSTOMER_KEY_MD5,
                sseKey.getMd5());
        // Calculate the MD5 hash of the encryption key and fill it in the
        // header, if the user didn't specify it in the metadata
        if (sseKey.getKey() != null
                && sseKey.getMd5() == null) {
            String encryptionKey_b64 = sseKey.getKey();
            byte[] encryptionKey = Base64.decode(encryptionKey_b64);
            request.addHeader(Headers.COPY_SOURCE_SERVER_SIDE_ENCRYPTION_CUSTOMER_KEY_MD5,
                    Md5Utils.md5AsBase64(encryptionKey));
        }
    }

    /**
     * Adds the specified header to the specified request, if the header value
     * is not null.
     *
     * @param request
     *            The request to add the header to.
     * @param header
     *            The header name.
     * @param value
     *            The header value.
     */
    private static void addHeaderIfNotNull(Request<?> request, String header, String value) {
        if (value != null) {
            request.addHeader(header, value);
        }
    }

    /**
     * <p>
     * Adds the specified date header in RFC 822 date format to the specified
     * request.
     * This method will not add a date header if the specified date value is <code>null</code>.
     * </p>
     *
     * @param request
     *            The request to add the header to.
     * @param header
     *            The header name.
     * @param value
     *            The header value.
     */
    private static void addDateHeader(Request<?> request, String header, Date value) {
        if (value != null) {
            request.addHeader(header, ServiceUtils.formatRfc822Date(value));
        }
    }

    /**
     * <p>
     * Adds the specified string list header, joined together separated with
     * commas, to the specified request.
     * This method will not add a string list header if the specified values
     * are <code>null</code> or empty.
     * </p>
     *
     * @param request
     *            The request to add the header to.
     * @param header
     *            The header name.
     * @param values
     *            The list of strings to join together for the header value.
     */
    private static void addStringListHeader(Request<?> request, String header, List<String> values) {
        if (values != null && !values.isEmpty()) {
            request.addHeader(header, ServiceUtils.join(values));
        }
    }

    /**
     * <p>
     * Adds response headers parameters to the request given, if non-null.
     * </p>
     *
     * @param request
     *            The request to add the response header parameters to.
     * @param responseHeaders
     *            The full set of response headers to add, or null for none.
     */
    private static void addResponseHeaderParameters(Request<?> request, ResponseHeaderOverrides responseHeaders) {
        if ( responseHeaders != null ) {
            if ( responseHeaders.getCacheControl() != null ) {
                request.addParameter(ResponseHeaderOverrides.RESPONSE_HEADER_CACHE_CONTROL, responseHeaders.getCacheControl());
            }
            if ( responseHeaders.getContentDisposition() != null ) {
                request.addParameter(ResponseHeaderOverrides.RESPONSE_HEADER_CONTENT_DISPOSITION,
                        responseHeaders.getContentDisposition());
            }
            if ( responseHeaders.getContentEncoding() != null ) {
                request.addParameter(ResponseHeaderOverrides.RESPONSE_HEADER_CONTENT_ENCODING,
                        responseHeaders.getContentEncoding());
            }
            if ( responseHeaders.getContentLanguage() != null ) {
                request.addParameter(ResponseHeaderOverrides.RESPONSE_HEADER_CONTENT_LANGUAGE,
                        responseHeaders.getContentLanguage());
            }
            if ( responseHeaders.getContentType() != null ) {
                request.addParameter(ResponseHeaderOverrides.RESPONSE_HEADER_CONTENT_TYPE, responseHeaders.getContentType());
            }
            if ( responseHeaders.getExpires() != null ) {
                request.addParameter(ResponseHeaderOverrides.RESPONSE_HEADER_EXPIRES, responseHeaders.getExpires());
            }
        }
    }

    /**
     * Returns the URL to the key in the bucket given, using the client's scheme
     * and endpoint. Returns null if the given bucket and key cannot be
     * converted to a URL.
     */
    public String getResourceUrl(String bucketName, String key) {
        try {
            return getUrl(bucketName, key).toString();
        } catch ( Exception e ) {
            return null;
        }
    }

    /**
     * Returns an URL for the object stored in the specified bucket and
     * key.
     * <p>
     * If the object identified by the given bucket and key has public read
     * permissions (ex: {@link CannedAccessControlList#PublicRead}), then this
     * URL can be directly accessed to retrieve the object's data.
     *
     * @param bucketName
     *            The name of the bucket containing the object whose URL is
     *            being requested.
     * @param key
     *            The key under which the object whose URL is being requested is
     *            stored.
     *
     * @return A unique URL for the object stored in the specified bucket and
     *         key.
     */
    public URL getUrl(String bucketName, String key) {
        Request<?> request = new DefaultRequest<Object>(Constants.S3_SERVICE_NAME);
        configRequest(request, bucketName, key);
        return ServiceUtils.convertRequestToUrl(request);
    }

    public Region getRegion() {
        String authority = super.endpoint.getAuthority();
        if (Constants.S3_HOSTNAME.equals(authority)) {
            return Region.US_Standard;
        }
        Matcher m = Region.S3_REGIONAL_ENDPOINT_PATTERN.matcher(authority);
        if (m.matches()) {
            return Region.fromValue(m.group(1));
        } else {
            throw new IllegalStateException("S3 client with invalid S3 endpoint configured");
        }
    }

    /**
     * Creates and initializes a new request object for the specified S3
     * resource. This method is responsible for determining the right way to
     * address resources. For example, bucket names that are not DNS addressable
     * cannot be addressed in V2, virtual host, style, and instead must use V1,
     * path style. The returned request object has the service name, endpoint
     * and resource path correctly populated. Callers can take the request, add
     * any additional headers or parameters, then sign and execute the request.
     *
     * @param bucketName
     *            An optional parameter indicating the name of the bucket
     *            containing the resource involved in the request.
     * @param key
     *            An optional parameter indicating the key under which the
     *            desired resource is stored in the specified bucket.
     * @param originalRequest
     *            The original request, as created by the user.
     * @param httpMethod
     *            The HTTP method to use when sending the request.
     *
     * @return A new request object, populated with endpoint, resource path, and
     *         service name, ready for callers to populate any additional
     *         headers or parameters, and execute.
     */
    protected <X extends AmazonWebServiceRequest> Request<X> createRequest(String bucketName, String key, X originalRequest, HttpMethodName httpMethod) {
        Request<X> request = new DefaultRequest<X>(originalRequest, Constants.S3_SERVICE_NAME);
        request.setHttpMethod(httpMethod);
        configRequest(request, bucketName, key);
        return request;
    }

    /**
     * Configure the given request with the specified bucket name and key.
     * @return the request configured
     */
    private void configRequest(
        Request<?> request, String bucketName, String key)
    {
        if ( !clientOptions.isPathStyleAccess()
             && BucketNameUtils.isDNSBucketName(bucketName)
             && !validIP(endpoint.getHost()) ) {
            request.setEndpoint(convertToVirtualHostEndpoint(bucketName));
            /*
             * If the key name starts with a slash character, in order to
             * prevent it being treated as a path delimiter, we need to add
             * another slash before the key name.
             * {@see com.amazonaws.http.HttpRequestFactory#createHttpRequest}
             */
            if (key != null && key.startsWith("/")) {
                key = "/" + key;
            }
            request.setResourcePath(key);
        } else {
            request.setEndpoint(endpoint);

            if (bucketName != null) {
                request.setResourcePath(bucketName + "/" + (key != null ? key : ""));
            }
        }
    }

    private boolean validIP(String IP) {
        if (IP == null) {
            return false;
        }
        String[] tokens = IP.split("\\.");
        if (tokens.length != 4) {
            return false;
        }
        for (String token : tokens) {
            int tokenInt;
            try {
                tokenInt = Integer.parseInt(token);
            } catch (NumberFormatException ase) {
                return false;
            }
            if (tokenInt < 0 || tokenInt > 255) {
                return false;
            }

        }
        return true;
    }

    private <X, Y extends AmazonWebServiceRequest> X invoke(Request<Y> request,
                                  Unmarshaller<X, InputStream> unmarshaller,
                                  String bucketName,
                                  String key) {
        return invoke(request, new S3XmlResponseHandler<X>(unmarshaller), bucketName, key);
    }

    @Override
    protected final ExecutionContext createExecutionContext(AmazonWebServiceRequest req) {
        boolean isMetricsEnabled = isRequestMetricsEnabled(req) || isProfilingEnabled();
        return new S3ExecutionContext(requestHandler2s, isMetricsEnabled, this);
    }

    private <X, Y extends AmazonWebServiceRequest> X invoke(Request<Y> request,
            HttpResponseHandler<AmazonWebServiceResponse<X>> responseHandler,
            String bucket, String key) {
        AmazonWebServiceRequest originalRequest = request.getOriginalRequest();
        checkHttps(originalRequest);
        ExecutionContext executionContext = createExecutionContext(originalRequest);
        // Retry V4 auth errors
        executionContext.setAuthErrorRetryStrategy(new S3V4AuthErrorRetryStrategy(bucket));
        AWSRequestMetrics awsRequestMetrics = executionContext.getAwsRequestMetrics();
        // Binds the request metrics to the current request.
        request.setAWSRequestMetrics(awsRequestMetrics);
        // Having the ClientExecuteTime defined here is not ideal (for the
        // timing measurement should start as close to the top of the call
        // stack of the service client method as possible)
        // but definitely a safe compromise for S3 at least for now.
        // We can incrementally make it more elaborate should the need arise
        // for individual method.
        awsRequestMetrics.startEvent(Field.ClientExecuteTime);
        Response<X> response = null;
        try {
            for (Entry<String, String> entry : request.getOriginalRequest()
                    .copyPrivateRequestParameters().entrySet()) {
                request.addParameter(entry.getKey(), entry.getValue());
            }
            request.setTimeOffset(timeOffset);
            /*
             * The string we sign needs to include the exact headers that we
             * send with the request, but the client runtime layer adds the
             * Content-Type header before the request is sent if one isn't set,
             * so we have to set something here otherwise the request will fail.
             */
            if (!request.getHeaders().containsKey(Headers.CONTENT_TYPE)) {
                request.addHeader(Headers.CONTENT_TYPE,
                    "application/x-www-form-urlencoded; charset=utf-8");
            }
            AWSCredentials credentials = awsCredentialsProvider.getCredentials();
            if (originalRequest.getRequestCredentials() != null) {
                credentials = originalRequest.getRequestCredentials();
            }
            executionContext.setSigner(createSigner(request, bucket, key));
            executionContext.setCredentials(credentials);
            response = client.execute(request, responseHandler,
                    errorResponseHandler, executionContext);
            return response.getAwsResponse();
        } catch (ResetException ex) {
            ex.setExtraInfo("If the request involves an input stream, the maximum stream buffer size can be configured via request.getRequestClientOptions().setReadLimit(int)");
            throw ex;
       } finally {
            endClientExecution(awsRequestMetrics, request, response);
        }
    }

    private void setZeroContentLength(Request<?> req) {
        // https://github.com/aws/aws-sdk-java/pull/215
        // http://aws.amazon.com/articles/1109#14
        req.addHeader(Headers.CONTENT_LENGTH, String.valueOf(0));
    }

    /**
     * Throws {@link IllegalArgumentException} if SSE customer key is in use
     * without https.
     */
    private void checkHttps(AmazonWebServiceRequest req) {
        if (req instanceof SSECustomerKeyProvider) {
            SSECustomerKeyProvider p = (SSECustomerKeyProvider) req;
            if (p.getSSECustomerKey() != null)
                assertHttps();
        } else if (req instanceof CopyObjectRequest) {
            CopyObjectRequest cor = (CopyObjectRequest) req;
            if (cor.getSourceSSECustomerKey() != null
            ||  cor.getDestinationSSECustomerKey() != null) {
                assertHttps();
            }
        } else if (req instanceof CopyPartRequest) {
            CopyPartRequest cpr = (CopyPartRequest) req;
            if (cpr.getSourceSSECustomerKey() != null
            ||  cpr.getDestinationSSECustomerKey() != null) {
                assertHttps();
            }
        }
    }

    private void assertHttps() {
        URI endpoint = this.endpoint;
        String scheme = endpoint == null ? null : endpoint.getScheme();
        if (!Protocol.HTTPS.toString().equalsIgnoreCase(scheme)) {
            throw new IllegalArgumentException(
                    "HTTPS must be used when sending customer encryption keys (SSE-C) to S3, in order to protect your encryption keys.");
        }
    }

    /**
     * For testing
     */
    URI getEndpoint() {
        return endpoint;
    }
}
