# MSS(Meituan Storage Service) SDK for Java

This is MSS SDK for Java。

## Introduction

### MSS服务介绍
	美团云存储服务（Meituan Storage Service, 简称MSS)，是美团云对外提供的云存储服务，其具备高可靠，安全，低成本等特性，并且其API兼容S3。MSS适合存放非结构化的数据，比如图片，视频，文档，备份等。

### MSS基本概念介绍
	MSS的API兼容S3, 其基本概念也和S3相同，主要包括Object, Bucket, Access Key, Secret Key等。
	Object对应一个文件，包括数据和元数据两部分。元数据以key-value的形式构成，它包含一些默认的元数据信息，比如Content-Type, Etag等，用户也可以自定义元数据。
	Bucket是object的容器，每个object都必须包含在一个bucket中。用户可以创建任意多个bucket。
	Access Key和Secret Key: 用户注册MSS时，系统会给用户分配一对Access Key和Secret Key, 用于标识用户，用户在使用API使用MSS服务时，需要使用这两个Key。请在美团云管理控制台查询AccessKey和SecretKey。

### MSS访问域名
	mtmss.com

## Installation
	下载MSS SDK for Java包后,进入MSS SDK for Java目录下，运行"mvn install",即可完成MSS SDK for Java的安装。
	新创建的MSS相关的maven项目，只需要在pom文件的<dependencies>下添加MSS SDK for Java依赖：
	<dependency>
        	<groupId>com.amazonaws</groupId>
        	<artifactId>mss-java-sdk-s3</artifactId>
        	<version>1.9.4</version>
        </dependency>
    注意：目前MSS SDK支持的Java版本包括Java1.7和Java1.8。
	

## Quick Start

### 创建AmazonS3Client
        package com.meituan.mss.s3test;

        import com.amazonaws.ClientConfiguration;
        import com.amazonaws.Protocol;
        import com.amazonaws.auth.AWSCredentials;
        import com.amazonaws.auth.BasicAWSCredentials;
        import com.amazonaws.services.s3.AmazonS3;
        import com.amazonaws.services.s3.AmazonS3Client;
        import com.amazonaws.services.s3.S3ClientOptions;
        import java.io.IOException;
        import java.io.InputStream;
        import java.net.URL;
        import java.util.Properties;

        public class AmazonS3ClientProvider {
                /**
                * <p>
                * 创建AmazonS3Client
                * </p>
                *
                *       @accessKey:mss用户的access key
                *        secretKey:mss用户的access secret
                *        url:mss server hostname,一般为mtmss.com
                */
                private static String accessKey = "*** accessKey ***";
                private static String secretKey = "*** secretKey ***";
                private static String url = "http://mtmss.com";
                static AmazonS3Client s3conn;
                
                public static AmazonS3 CreateAmazonS3Conn()
                            throws IOException{
                        AWSCredentials credentials = new BasicAWSCredentials(accessKey, secretKey);
                        ClientConfiguration clientConfig = new ClientConfiguration();
                        //clientConfig.setSignerOverride("S3SignerType");

                        URL ep = new URL(url);
                        if (ep.getProtocol().equalsIgnoreCase("http")) {
                                clientConfig.setProtocol(Protocol.HTTP);
                        } else if (ep.getProtocol().equalsIgnoreCase("https")) {
                                clientConfig.setProtocol(Protocol.HTTPS);
                        } else {
                                throw new IOException("Unsupported protocol");
                        }
                        String endpoint = ep.getHost();
                        if (ep.getPort() > 0) {
                                endpoint += ":" + ep.getPort();
                        }

                        S3ClientOptions s3ClientOptions = new S3ClientOptions();
                        // mss only support path style access.
                        s3ClientOptions.setPathStyleAccess(true);
                        s3conn = new AmazonS3Client(credentials, clientConfig);
                        s3conn.setS3ClientOptions(s3ClientOptions);
                        s3conn.setEndpoint(endpoint);
                        //s3conn.setSignerRegionOverride("S3SignerType");
                        return s3conn;
                }
        }

### 创建Bucket

        import com.amazonaws.AmazonClientException;
        import com.amazonaws.AmazonServiceException;
        import com.amazonaws.services.s3.AmazonS3;
        import com.amazonaws.services.s3.model.AccessControlList;
        import com.amazonaws.services.s3.model.Bucket;
        import com.meituan.mss.s3test.AmazonS3ClientProvider;
        public class CreateBucket {
                private static String newBucketName = "mss-test-bucket";
                private static AmazonS3 s3Client = AmazonS3ClientProvider.CreateAmazonS3Conn();
                public void testCreateBucket_NewBucketNameMatches() {
                        try {
                                Bucket bucket = s3Client.createBucket(newBucketName);
                        } catch (AmazonServiceException ase) {
                                System.out.println("Caught an AmazonServiceException, which" +
                                        " means your request made it " +
                                        "to Amazon S3, but was rejected with an error response" +
                                        " for some reason.");
                                System.out.println("Error Message:    " + ase.getMessage());
                                System.out.println("HTTP Status Code: " + ase.getStatusCode());
                                System.out.println("AWS Error Code:   " + ase.getErrorCode());
                                System.out.println("Error Type:       " + ase.getErrorType());
                                System.out.println("Request ID:       " + ase.getRequestId());
                        } catch (AmazonClientException ace) {
                                System.out.println("Caught an AmazonClientException, which means"+
                                " the client encountered " +
                                "an internal error while trying to " +
                                "communicate with S3, " +
                                "such as not being able to access the network.");
                                System.out.println("Error Message: " + ace.getMessage());
                        }
                }
        }

### 上传文件

        import java.io.File;
        import java.io.IOException;

        import com.amazonaws.AmazonClientException;
        import com.amazonaws.AmazonServiceException;
        import com.amazonaws.auth.profile.ProfileCredentialsProvider;
        import com.amazonaws.services.s3.AmazonS3;
        import com.amazonaws.services.s3.AmazonS3Client;
        import com.amazonaws.services.s3.model.PutObjectRequest;
        import com.meituan.mss.s3test.AmazonS3ClientProvider;
        
        public class UploadObjectSingleOperation {
                private static String bucketName     = "*** Provide bucket name ***";
                private static String objectName        = "*** objectName ***";
                private static AmazonS3 s3Client = AmazonS3ClientProvider.CreateAmazonS3Conn();

                public void testUploadObject() {
                        try {
                                System.out.println("Uploading a new object to S3 from a file\n");
                                File file = new File(uploadFileName);
                                s3client.putObject(new PutObjectRequest(
                                             bucketName, objectName, file));

                        } catch (AmazonServiceException ase) {
                                System.out.println("Caught an AmazonServiceException, which " +
                                "means your request made it " +
                                "to Amazon S3, but was rejected with an error response" +
                                " for some reason.");
                                System.out.println("Error Message:    " + ase.getMessage());
                                System.out.println("HTTP Status Code: " + ase.getStatusCode());
                                System.out.println("AWS Error Code:   " + ase.getErrorCode());
                                System.out.println("Error Type:       " + ase.getErrorType());
                                System.out.println("Request ID:       " + ase.getRequestId());
                        } catch (AmazonClientException ace) {
                                System.out.println("Caught an AmazonClientException, which " +
                                "means the client encountered " +
                                "an internal error while trying to " +
                                "communicate with S3, " +
                                "such as not being able to access the network.");
                                System.out.println("Error Message: " + ace.getMessage());
                        }
                    }
        }

### 下载文件
        import java.io.BufferedReader;
        import java.io.IOException;
        import java.io.InputStream;
        import java.io.InputStreamReader;

        import com.amazonaws.AmazonClientException;
        import com.amazonaws.AmazonServiceException;
        import com.amazonaws.auth.profile.ProfileCredentialsProvider;
        import com.amazonaws.services.s3.AmazonS3;
        import com.amazonaws.services.s3.AmazonS3Client;
        import com.amazonaws.services.s3.model.GetObjectRequest;
        import com.amazonaws.services.s3.model.S3Object;
        import com.meituan.mss.s3test.AmazonS3ClientProvider;
        public class GetObject {
                private static String bucketName = "*** provide bucket name ***"; 
                private static String objectName        = "*** objectName ***";
                private static AmazonS3 s3Client = AmazonS3ClientProvider.CreateAmazonS3Conn();
                public void getObject() {
                        try {
                                System.out.println("Downloading an object");
                                S3Object s3object = s3Client.getObject(new GetObjectRequest(
                                             bucketName, objectName));
                                System.out.println("Content-Type: "  +
                                s3object.getObjectMetadata().getContentType());
                                InputStream objectData = s3object.getObjectContent()        
                        } catch (AmazonServiceException ase) {
                                System.out.println("Caught an AmazonServiceException, which" +
                                            " means your request made it " +
                                            "to Amazon S3, but was rejected with an error response" +
                                            " for some reason.");
                                System.out.println("Error Message:    " + ase.getMessage());
                                System.out.println("HTTP Status Code: " + ase.getStatusCode());
                                System.out.println("AWS Error Code:   " + ase.getErrorCode());
                                System.out.println("Error Type:       " + ase.getErrorType());
                                System.out.println("Request ID:       " + ase.getRequestId());
                        } catch (AmazonClientException ace) {
                                System.out.println("Caught an AmazonClientException, which means"+
                                    " the client encountered " +
                                    "an internal error while trying to " +
                                    "communicate with S3, " +
                                    "such as not being able to access the network.");
                                System.out.println("Error Message: " + ace.getMessage());
                        }
                }
        }

### s3 presign
        import com.amazonaws.AmazonClientException;
        import com.amazonaws.AmazonServiceException;
        import com.amazonaws.HttpMethod;
        import com.amazonaws.services.s3.AmazonS3;
        import com.amazonaws.services.s3.model.Bucket;
        import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest;
        import com.amazonaws.services.s3.model.ObjectMetadata;
        import com.meituan.mss.s3test.AmazonS3ClientProvider;
        import com.meituan.mss.s3test.factory.TestS3ObjectFactory;
        import junit.framework.Assert;
        import org.junit.After;
        import org.junit.Before;
        import org.junit.Test;

        import java.io.IOException;
        import java.io.InputStream;
        import java.net.URL;
        import java.util.Date;
        import org.apache.commons.httpclient.*;
        import org.apache.commons.httpclient.methods.*;
        import com.amazonaws.services.s3.model.ResponseHeaderOverrides;

        import java.io.*;
        public class PreSignURL {
                private static String bucketName = "*** provide bucket name ***";
                private static String objectName        = "*** objectName ***";
                private static AmazonS3 s3Client = AmazonS3ClientProvider.CreateAmazonS3Conn();
                public void presigntest() {
                        try {
                                System.out.println("Generating pre-signed URL.");
                                java.util.Date expiration = new java.util.Date();
                                long milliSeconds = expiration.getTime();
                                milliSeconds += 1000 * 60 * 5; // Add 1 hour.
                                expiration.setTime(milliSeconds);
                                //generate Get Bucket presign request
                                GeneratePresignedUrlRequest generatePresignedUrlRequest =
                                                    new GeneratePresignedUrlRequest(bucketName, "");
                                generatePresignedUrlRequest.setMethod(HttpMethod.GET);
                                generatePresignedUrlRequest.setExpiration(expiration);
                                URL url = s3Client.generatePresignedUrl(generatePresignedUrlRequest);

                                System.out.println("Pre-Signed URL = " + url.toString());
                                String url_str = url.toString();
                                HttpClient client = new HttpClient();
                                GetMethod method = new GetMethod(url_str);
                                try {
                                        int statusCode = client.executeMethod(method);
                                        Assert.assertEquals(statusCode, 200);

                                } catch (HttpException e) {
                                        System.err.println("Fatal protocol violation: " + e.getMessage());
                                        e.printStackTrace();
                                } catch (IOException e) {
                                        System.err.println("Fatal transport error: " + e.getMessage());
                                        e.printStackTrace();
                                } finally {
                                        // Release the connection.
                                        method.releaseConnection();
                                }

                        } catch (AmazonServiceException exception) {
                                System.out.println("Caught an AmazonServiceException, " +
                                                    "which means your request made it " +
                                                    "to Amazon S3, but was rejected with an error response " +
                                                    "for some reason.");
                                System.out.println("Error Message: " + exception.getMessage());
                                System.out.println("HTTP  Code: "    + exception.getStatusCode());
                                System.out.println("AWS Error Code:" + exception.getErrorCode());
                                System.out.println("Error Type:    " + exception.getErrorType());
                                System.out.println("Request ID:    " + exception.getRequestId());
                        } catch (AmazonClientException ace) {
                                System.out.println("Caught an AmazonClientException, " +
                                                    "which means the client encountered " +
                                                    "an internal error while trying to communicate" +
                                                    " with S3, " +
                                                    "such as not being able to access the network.");
                                System.out.println("Error Message: " + ace.getMessage());
                        }

                }
        }
