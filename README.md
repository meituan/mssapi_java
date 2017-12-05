# S3 JAVA SDK


标签（空格分隔）： 未分类

---

#安装与初始化#
##安装##
下载MSS SDK for Java包后,进入MSS SDK for Java目录下，运行"mvn install",即可完成MSS SDK for Java的安装。
<br>新创建的MSS相关的maven项目，只需要在pom文件的<dependencies>下添加MSS SDK for Java依赖：
```
<dependency>
    	<groupId>com.amazonaws</groupId>
    	<artifactId>mss-java-sdk-s3</artifactId>
    	<version>1.9.4</version>
    </dependency>
    
```
**注意：**目前MSS SDK支持的Java版本包括Java1.6，Java1.7和Java1.8。
##确定Endpoint##
|示例|说明|
|----|----|
|northchina1.mtmss.com|	公网访问北京区域的Bucket|
|eastchina1.mtmss.com|	公网访问上海区域的Bucket|

##初始化MSSClient##
向MSS发送任一HTTP请求之前，必须先创建一个MSSClient实例:
```
//accessKey:用户的Access Key ID
//secretKey:用户的Access Key Secret
//hostname:MSS的endpoint服务地址
public static AmazonS3 CreateAmazonS3Conn (String accessKey, String secretKey, String hostname){
    AWSCredentials credentials = new BasicAWSCredentials(accessKey, secretKey);
  
    //生成云存储api client
    mssClient = new AmazonS3Client(credentials);
  
    //配置云存储服务地址
    mssClient.setEndpoint(hostname);
  
    //设置客户端生成的http请求hos格式，目前只支持path type的格式，不支持bucket域名的格式
    S3ClientOptions s3ClientOptions = new S3ClientOptions();
    s3ClientOptions.setPathStyleAccess(true);
    mssClient.setS3ClientOptions(s3ClientOptions);
    return mssClient;
}
```


----------


#快速入门#
##初始化MSSClient##
向MSS发送任一HTTP请求之前，必须先创建一个MSSClient实例:
```
//accessKey:用户的Access Key ID
//secretKey:用户的Access Key Secret
//hostname:MSS的endpoint服务地址
public static AmazonS3 CreateAmazonS3Conn (String accessKey, String secretKey, String hostname){
    AWSCredentials credentials = new BasicAWSCredentials(accessKey, secretKey);
 
    //生成云存储api client
    mssClient = new AmazonS3Client(credentials);
 
    //配置云存储服务地址
    mssClient.setEndpoint(hostname);
 
    //设置客户端生成的http请求hos格式，目前只支持path type的格式，不支持bucket域名的格式
    S3ClientOptions s3ClientOptions = new S3ClientOptions();
    s3ClientOptions.setPathStyleAccess(true);
    mssClient.setS3ClientOptions(s3ClientOptions);
    return mssClient;
}

```
##创建Bucket##
存储空间(Bucket)是用户账号下的一个命名空间，相当于数据的容器，可以存储若干文件(Object)，在上传文件前，必须先创建好Bucket。以下代码展示如何新建一个Bucket：
```
public void createBucketIfNotExistExample(String bucketName){
    try{
        //判断待创建的bucket是否存在，如果存在不用重复创建，重复创建同名bucket服务器端会返回错误
        if (s3client.doesBucketExist(bucketName) == false) {
            s3client.createBucket(bucketName);
        }
    }catch (AmazonServiceException ase) {
        //存储服务端处理异常
        System.out.println("Caught an ServiceException.");
        System.out.println("Error Message:    " + ase.getMessage());
        System.out.println("HTTP Status Code: " + ase.getStatusCode());
        System.out.println("Error Code:   " + ase.getErrorCode());
        System.out.println("Error Type:       " + ase.getErrorType());
        System.out.println("Request ID:       " + ase.getRequestId());
        Assert.assertEquals(true, true);
    }catch (AmazonClientException ace) {
        //客户端处理异常
        System.out.println("Caught an ClientException.");
        System.out.println("Error Message: " + ace.getMessage());
    }
    }
```
    
##上传Object##
以下代码展示如何上传文件(object)至MSS：
```
public void putObjectExample(String bucketName, String objectName, String content){
    try{
        //bucketName指定上传文件所在的桶名
        //objectName指定上传的文件名
        //content指定上传的文件内容
        s3client.putObject(bucketName,objectName,new ByteArrayInputStream(content.getBytes()),null);
    }catch (AmazonServiceException ase) {
        //存储服务端处理异常
        System.out.println("Caught an ServiceException.");
        System.out.println("Error Message:    " + ase.getMessage());
        System.out.println("HTTP Status Code: " + ase.getStatusCode());
        System.out.println("Error Code:   " + ase.getErrorCode());
        System.out.println("Error Type:       " + ase.getErrorType());
        System.out.println("Request ID:       " + ase.getRequestId());
        Assert.assertEquals(true, true);
    }catch (AmazonClientException ace) {
        //客户端处理异常
        System.out.println("Caught an ClientException.");
        System.out.println("Error Message: " + ace.getMessage());
    }
}
```

##下载Object##
以下代码展示如何获取Object的文本内容：
```
public void getObjectExample(String bucketName, String objectName) throws IOException{
    try{
        //bucketName是桶名
        //objectName是文件名
        S3Object s3object = s3client.getObject(new GetObjectRequest(
                bucketName, objectName));
        InputStream content = s3object.getObjectContent();
        BufferedReader reader = new BufferedReader(new InputStreamReader(content));
        if (content != null) {
                while (true) {
                    String line = reader.readLine();
                    if (line == null) break;
                    System.out.println("\n" + line);
                }
            //获取object后需要close(),释放连接
            s3object.close();
        }
    }catch (AmazonServiceException ase) {
        //存储服务端处理异常
        System.out.println("Caught an ServiceException.");
        System.out.println("Error Message:    " + ase.getMessage());
        System.out.println("HTTP Status Code: " + ase.getStatusCode());
        System.out.println("Error Code:   " + ase.getErrorCode());
        System.out.println("Error Type:       " + ase.getErrorType());
        System.out.println("Request ID:       " + ase.getRequestId());
        Assert.assertEquals(true, true);
    }catch (AmazonClientException ace) {
        //客户端处理异常
        System.out.println("Caught an ClientException.");
        System.out.println("Error Message: " + ace.getMessage());
    }
}
```

##列举Object##
当完成一系列上传Object操作后，可能需要查看Bucket下包含哪些Object。以下代码展示如何列举指定Bucket下的Object：
```
//列举object
public void objectListExample(String bucketName){
    try{
        List<S3ObjectSummary> objSum = s3client.listObjects(bucketName).getObjectSummaries();
        for(int i = 0; i < objSum.size(); i++) {
            System.out.println(objSum.get(i).getKey().toString());
        }
    }catch (AmazonServiceException ase) {
        //存储服务端处理异常
        System.out.println("Caught an ServiceException.");
        System.out.println("Error Message:    " + ase.getMessage());
        System.out.println("HTTP Status Code: " + ase.getStatusCode());
        System.out.println("Error Code:   " + ase.getErrorCode());
        System.out.println("Error Type:       " + ase.getErrorType());
        System.out.println("Request ID:       " + ase.getRequestId());
        Assert.assertEquals(true, true);
    }catch (AmazonClientException ace) {
        //客户端处理异常
        System.out.println("Caught an ClientException.");
        System.out.println("Error Message: " + ace.getMessage());
    }
}
```
##删除Object##
以下代码展示如何删除指定Object：
```
public void deleteObjectExample(String bucketName, String objectName){
    try{
        s3client.deleteObject(new DeleteObjectRequest(bucketName, objectName));
    }catch (AmazonServiceException ase) {
        //存储服务端处理异常
        System.out.println("Caught an ServiceException.");
        System.out.println("Error Message:    " + ase.getMessage());
        System.out.println("HTTP Status Code: " + ase.getStatusCode());
        System.out.println("Error Code:   " + ase.getErrorCode());
        System.out.println("Error Type:       " + ase.getErrorType());
        System.out.println("Request ID:       " + ase.getRequestId());
        Assert.assertEquals(true, true);
    }catch (AmazonClientException ace) {
        //客户端处理异常
        System.out.println("Caught an ClientException.");
        System.out.println("Error Message: " + ace.getMessage());
    }
}
```


----------


#管理存储空间#
##创建Bucket##
您可以使用MSSClient.createBucket创建Bucket。如下代码展示如何新建一个Bucket：
```
public void createBucketIfNotExistExample(String bucketName){
    try{
        //判断待创建的bucket是否存在，如果存在不用重复创建，重复创建同名bucket服务器端会返回错误
        if (s3client.doesBucketExist(bucketName) == false) {
            s3client.createBucket(bucketName);
        }
    }catch (AmazonServiceException ase) {
        //存储服务端处理异常
        System.out.println("Caught an ServiceException.");
        System.out.println("Error Message:    " + ase.getMessage());
        System.out.println("HTTP Status Code: " + ase.getStatusCode());
        System.out.println("Error Code:   " + ase.getErrorCode());
        System.out.println("Error Type:       " + ase.getErrorType());
        System.out.println("Request ID:       " + ase.getRequestId());
    }catch (AmazonClientException ace) {
        //客户端处理异常
        System.out.println("Caught an ClientException.");
        System.out.println("Error Message: " + ace.getMessage());
    }
    }
```

##列举Bucket##
您可以使用MSSClient.listBuckets列举指定用户下的Bucket。
```
public void listBuckets(){
    try{
        // 列举bucket
        List<Bucket> buckets = s3client.listBuckets();
        for (Bucket bucket : buckets) {
            System.out.println(bucket.getName());
        }
    }catch (AmazonServiceException ase) {
        //存储服务端处理异常
        System.out.println("Caught an ServiceException.");
        System.out.println("Error Message:    " + ase.getMessage());
        System.out.println("HTTP Status Code: " + ase.getStatusCode());
        System.out.println("Error Code:   " + ase.getErrorCode());
        System.out.println("Error Type:       " + ase.getErrorType());
        System.out.println("Request ID:       " + ase.getRequestId());
    }catch (AmazonClientException ace) {
        //客户端处理异常
        System.out.println("Caught an ClientException.");
        System.out.println("Error Message: " + ace.getMessage());
    }
}
```

##删除Bucket##
您可以使用MSSClient.deleteBucket删除Bucket。以下代码展示如何删除一个Bucket：
```
public void deleteBucketExample(String bucketName){
        try{
            if (s3client.doesBucketExist(bucketName)) {
                s3client.deleteBucket(bucketName);
            }
        }catch (AmazonServiceException ase) {
            //存储服务端处理异常
            System.out.println("Caught an ServiceException.");
            System.out.println("Error Message:    " + ase.getMessage());
            System.out.println("HTTP Status Code: " + ase.getStatusCode());
            System.out.println("Error Code:   " + ase.getErrorCode());
            System.out.println("Error Type:       " + ase.getErrorType());
            System.out.println("Request ID:       " + ase.getRequestId());
        }catch (AmazonClientException ace) {
            //客户端处理异常
            System.out.println("Caught an ClientException.");
            System.out.println("Error Message: " + ace.getMessage());
        }
    }
```

##判断Bucket是否存在##
您可以使用MSSClient.doesBucketExist接口判断该Bucket是否已存在。以下代码展示如何判断指定Bucket是否存在：
```
public void checkBucketExistExample(String bucketName){
    try{
        boolean exist = s3client.doesBucketExist(bucketName);
    }catch (AmazonServiceException ase) {
        //存储服务端处理异常
        System.out.println("Caught an ServiceException.");
        System.out.println("Error Message:    " + ase.getMessage());
        System.out.println("HTTP Status Code: " + ase.getStatusCode());
        System.out.println("Error Code:   " + ase.getErrorCode());
        System.out.println("Error Type:       " + ase.getErrorType());
        System.out.println("Request ID:       " + ase.getRequestId());
    }catch (AmazonClientException ace) {
        //客户端处理异常
        System.out.println("Caught an ClientException.");
        System.out.println("Error Message: " + ace.getMessage());
    }
}
```

##设置Bucket ACL##
Bucket的ACL包含三类：Private（私有读写）, PublicRead（公共读私有写）。您可以通过MSSClient.setBucketAcl设置bucket的权限。
|权限|Java SDK对应值|
|----|-----|
|私有读写|	CannedAccessControlList.Private|
|公共读私有写|	CannedAccessControlList.PublicRead|

以下代码展示如何设置Bucket的权限：
```
public void setBucketAclExample(String bucketName){
    try{
        // 将桶设置为公共读、私有写
        s3client.setBucketAcl(bucketName, CannedAccessControlList.PublicRead);
    }catch (AmazonServiceException ase) {
        //存储服务端处理异常
        System.out.println("Caught an ServiceException.");
        System.out.println("Error Message:    " + ase.getMessage());
        System.out.println("HTTP Status Code: " + ase.getStatusCode());
        System.out.println("Error Code:   " + ase.getErrorCode());
        System.out.println("Error Type:       " + ase.getErrorType());
        System.out.println("Request ID:       " + ase.getRequestId());
    }catch (AmazonClientException ace) {
        //客户端处理异常
        System.out.println("Caught an ClientException.");
        System.out.println("Error Message: " + ace.getMessage());
    }
}
```

##获取Bucket ACL##
您可以通过MSSClient.getBucketAcl获取bucket的权限。以下代码展示如何获取Bucket的ACL：
```
public void getBucketAclExample(String bucketName){
    try{
        AccessControlList bucketacl = s3client.getBucketAcl(bucketName);
        System.out.println("Bucket acl:" + bucketacl.toString() );
    }catch (AmazonServiceException ase) {
        //存储服务端处理异常
        System.out.println("Caught an ServiceException.");
        System.out.println("Error Message:    " + ase.getMessage());
        System.out.println("HTTP Status Code: " + ase.getStatusCode());
        System.out.println("Error Code:   " + ase.getErrorCode());
        System.out.println("Error Type:       " + ase.getErrorType());
        System.out.println("Request ID:       " + ase.getRequestId());
        Assert.assertEquals(true, true);
    }catch (AmazonClientException ace) {
        //客户端处理异常
        System.out.println("Caught an ClientException.");
        System.out.println("Error Message: " + ace.getMessage());
    }
}
```
##Bucket Policy（策略）管理##
###设置policy###
```
public void setBucketPolicy(String bucketName, String policyJsonContent){
    try{
        SetBucketPolicyRequest req = new SetBucketPolicyRequest(bucketName,policyJsonContent);
        s3client.setBucketPolicy(req);
    }catch (AmazonServiceException ase) {
        //存储服务端处理异常
        System.out.println("Caught an ServiceException.");
        System.out.println("Error Message:    " + ase.getMessage());
        System.out.println("HTTP Status Code: " + ase.getStatusCode());
        System.out.println("Error Code:   " + ase.getErrorCode());
        System.out.println("Error Type:       " + ase.getErrorType());
        System.out.println("Request ID:       " + ase.getRequestId());
    }catch (AmazonClientException ace) {
        //客户端处理异常
        System.out.println("Caught an ClientException.");
        System.out.println("Error Message: " + ace.getMessage());
    }
}
```

###获取policy###
```
public void getBucketPolicy(String bucketName){
    try{
        GetBucketPolicyRequest getReq = new GetBucketPolicyRequest(bucketName);
        BucketPolicy policy = s3client.getBucketPolicy(getReq);
        System.out.println("policy: " + policy.getPolicyText());
    }catch (AmazonServiceException ase) {
        //存储服务端处理异常
        System.out.println("Caught an ServiceException.");
        System.out.println("Error Message:    " + ase.getMessage());
        System.out.println("HTTP Status Code: " + ase.getStatusCode());
        System.out.println("Error Code:   " + ase.getErrorCode());
        System.out.println("Error Type:       " + ase.getErrorType());
        System.out.println("Request ID:       " + ase.getRequestId());
    }catch (AmazonClientException ace) {
        //客户端处理异常
        System.out.println("Caught an ClientException.");
        System.out.println("Error Message: " + ace.getMessage());
    }
}
```

###删除policy###
```
public void deleteBucketPolicy(String bucketName){
    try{
        DeleteBucketPolicyRequest delReq = new DeleteBucketPolicyRequest(bucketName);
        s3client.deleteBucketPolicy(delReq);
    }catch (AmazonServiceException ase) {
        //存储服务端处理异常
        System.out.println("Caught an ServiceException.");
        System.out.println("Error Message:    " + ase.getMessage());
        System.out.println("HTTP Status Code: " + ase.getStatusCode());
        System.out.println("Error Code:   " + ase.getErrorCode());
        System.out.println("Error Type:       " + ase.getErrorType());
        System.out.println("Request ID:       " + ase.getRequestId());
    }catch (AmazonClientException ace) {
        //客户端处理异常
        System.out.println("Caught an ClientException.");
        System.out.println("Error Message: " + ace.getMessage());
    }
}
```

###如何基于policy设置Referer###
```
白名单设置（不允许空referer）
配置域名 http://www.example.com http://example.com
{
              "Version":"2012-10-17",
              "Statement":[
              {
                     "Sid":"Allow get requests without referrer",
                     "Effect":"Deny",
                     "Principal":{"AWS": "*"},
                     "Action":["s3:GetObject"],
                     "Resource":["arn:aws:s3:::examplebucket/*"],
                     "Condition":{
                             "StringNotLike":{"aws:Referer":["http://www.example.com/*","http://example.com/*"]}
                     }
              }
              ]
}
  
白名单设置（允许空referer）
配置域名 http://www.example.com http://example.com
{
              "Version":"2012-10-17",
              "Statement":[
              {
                     "Sid":"Allow get requests without referrer",
                     "Effect":"Deny",
                     "Principal":"*",
                     "Action":["s3:GetObject"],
                     "Resource":"arn:aws:s3:::examplebucket/*",
                     "Condition":{
                             "StringNotLike":{"aws:Referer":["http://www.example.com/*","http://example.com/*",""]}
                     }
              }
              ]
}
  
  
黑名单设置（不允许空referer）
{
              "Version":"2012-10-17",
              "Statement":[
              {
                     "Sid":"Allow get requests without referrer",
                     "Effect":"Deny",
                     "Principal":"*",
                     "Action":["s3:GetObject"],
                     "Resource":"arn:aws:s3:::examplebucket/*",
                     "Condition":{
                             "StringLike":{"aws:Referer":["http://www.example.com/*","http://example.com/*",""]}
                     }
              }
              ]
}
  
黑名单设置（允许空referer）
{
              "Version":"2012-10-17",
              "Id":"http referer policy example",
              "Statement":[
              {
                     "Sid":"Allow get requests without referrer",
                     "Effect":"Deny",
                     "Principal":{"AWS": "*"},
                     "Action":["s3:GetObject"],
                     "Resource":["arn:aws:s3:::examplebucket/*"],
                     "Condition":{
                             "StringLike":{"aws:Referer":["http://www.example.com/*","http://example.com/*"]}
                     }
              }
              ]
}
```


----------


#上传文件#
在MSS中，用户操作的基本数据单元是文件（Object）。MSS Java SDK提供了丰富的文件上传接口，可以通过以下方式上传文件：

 - 流式上传
 - 文件上传
 - 分片上传

流式上传、文件上传的文件（Object）最大不能超过5GB。当文件较大时，请使用分片上传。
##上传字符串##
```
public void putObjectExample(String bucketName, String objectName, String content){
    try{
        //bucketName指定上传文件所在的桶名
        //objectName指定上传的文件名
        //content指定上传的文件内容
        s3client.putObject(bucketName,objectName,new ByteArrayInputStream(content.getBytes()),null);
    }catch (AmazonServiceException ase) {
        //存储服务端处理异常
        System.out.println("Caught an ServiceException.");
        System.out.println("Error Message:    " + ase.getMessage());
        System.out.println("HTTP Status Code: " + ase.getStatusCode());
        System.out.println("Error Code:   " + ase.getErrorCode());
        System.out.println("Error Type:       " + ase.getErrorType());
        System.out.println("Request ID:       " + ase.getRequestId());
    }catch (AmazonClientException ace) {
        //客户端处理异常
        System.out.println("Caught an ClientException.");
        System.out.println("Error Message: " + ace.getMessage());
    }
}
```

##上传网络流##
```
public void pubObjectInputStreamExample(String bucketName, String objectName){
    try{
        //bucketName指定上传文件所在的桶名
        //objectName指定上传的文件名
        //content指定上传的文件内容
        InputStream inputStream = new URL("https://mtyun.com/").openStream();
        s3client.putObject(bucketName,objectName,inputStream,null);
    }catch (AmazonServiceException ase) {
        //存储服务端处理异常
        System.out.println("Caught an ServiceException.");
        System.out.println("Error Message:    " + ase.getMessage());
        System.out.println("HTTP Status Code: " + ase.getStatusCode());
        System.out.println("Error Code:   " + ase.getErrorCode());
        System.out.println("Error Type:       " + ase.getErrorType());
        System.out.println("Request ID:       " + ase.getRequestId());
    }catch (AmazonClientException ace) {
        //客户端处理异常
        System.out.println("Caught an ClientException.");
        System.out.println("Error Message: " + ace.getMessage());
    }catch(IOException e)
    {
        e.printStackTrace();
    }
}
```

##上传本地文件##
```
public void putObjectFileExample(String bucketName, String objectName){
    try{
        String uploadFileName = "/Users/junechen/learningDir/s3-mirrorserver/src/store/index.html";
        FileInputStream inputStream = new FileInputStream(uploadFileName);
        s3client.putObject(bucketName,objectName,inputStream,null);
    }catch (AmazonServiceException ase) {
        //存储服务端处理异常
        System.out.println("Caught an ServiceException.");
        System.out.println("Error Message:    " + ase.getMessage());
        System.out.println("HTTP Status Code: " + ase.getStatusCode());
        System.out.println("Error Code:   " + ase.getErrorCode());
        System.out.println("Error Type:       " + ase.getErrorType());
        System.out.println("Request ID:       " + ase.getRequestId());
    }catch (AmazonClientException ace) {
        //客户端处理异常
        System.out.println("Caught an ClientException.");
        System.out.println("Error Message: " + ace.getMessage());
    }catch(IOException e)
    {
        e.printStackTrace();
    }
}
```

##设置元信息##
文件元信息(Object Meta)，是对用户上传到MSS的文件的属性描述，分为两种：HTTP标准属性（HTTP Headers）和User Meta（用户自定义元信息）。 文件元信息可以在各种方式上传（流上传、文件上传、追加上传、分片上传、断点续传），或拷贝文件时进行设置。元信息的名称大小写不敏感。
<br>设定http header
 
|名称|描述|默认值|
|---|---|---|
|Content-MD5	|文件数据校验，设置了该值后MSS会启用文件内容MD5校验，把您提供的MD5与文件的MD5比较，不一致会抛出错误|	无|
|Content-Type|	文件的MIME，定义文件的类型及网页编码，决定浏览器将以什么形式、什么编码读取文件。如果用户没有指定则根据Key或文件名的扩展名生成，如果没有扩展名则填默认值|	application/octet-stream|
|Content-Disposition|	指示MINME用户代理如何显示附加的文件，打开或下载，及文件名称	|无|
|Expires|	缓存过期时间，MSS未使用，格式是格林威治时间（GMT）|	无|
|Cache-Control|	指定该Object被下载时的网页的缓存行为|	无|
|Content-Encoding	|传输内容编码|-|

```
public void putObjectWithMetaExample(String bucketName,String objectName){
    try{
        String objectContent = "hello world.";
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentType("text/html");
        metadata.setContentDisposition("testContentDisposition");
        s3client.putObject(new PutObjectRequest(
                bucketName, objectName, new ByteArrayInputStream(objectContent.getBytes()), metadata));
    }catch (AmazonServiceException ase) {
        //存储服务端处理异常
        System.out.println("Caught an ServiceException.");
        System.out.println("Error Message:    " + ase.getMessage());
        System.out.println("HTTP Status Code: " + ase.getStatusCode());
        System.out.println("Error Code:   " + ase.getErrorCode());
        System.out.println("Error Type:       " + ase.getErrorType());
        System.out.println("Request ID:       " + ase.getRequestId());
    }catch (AmazonClientException ace) {
        //客户端处理异常
        System.out.println("Caught an ClientException.");
        System.out.println("Error Message: " + ace.getMessage());
    }
}
```

##用户自定义元信息##
MSS支持用户自定义Object的元信息，对Object进行描述。
```
public void putObjectWithSelfDefineMetaExample(String bucketName,String objectName){
    try{
        String objectContent = "hello world.";
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.addUserMetadata("filename","test.txt");
        s3client.putObject(new PutObjectRequest(
                bucketName, objectName, new ByteArrayInputStream(objectContent.getBytes()), metadata));
    }catch (AmazonServiceException ase) {
        //存储服务端处理异常
        System.out.println("Caught an ServiceException.");
        System.out.println("Error Message:    " + ase.getMessage());
        System.out.println("HTTP Status Code: " + ase.getStatusCode());
        System.out.println("Error Code:   " + ase.getErrorCode());
        System.out.println("Error Type:       " + ase.getErrorType());
        System.out.println("Request ID:       " + ase.getRequestId());
    }catch (AmazonClientException ace) {
        //客户端处理异常
        System.out.println("Caught an ClientException.");
        System.out.println("Error Message: " + ace.getMessage());
    }
}
```

##分片上传##
对于大文件上传，可以切分成片上传。用户可以在如下的应用场景内（但不仅限于此），使用分片上传(Multipart Upload)模式：

 - 需要支持断点上传。
 - 上传超过100MB大小的文件。
 - 网络条件较差，和MSS的服务器之间的链接经常断开。

分片上传(Multipart Upload)分为如下3个步骤:

 - 初始化一个分片上传任务（InitiateMultipartUpload）
 - 逐个或并行上传分片（UploadPart）
 - 完成分片上传（CompleteMultipartUpload）或取消分片上传(AbortMultipartUpload)

###分步完成Multipart Upload###
使用Multipart Upload模式传输数据前，必须先通知MSS初始化一个Multipart Upload事件。该操作会返回一个MSS服务器创建的全局唯一的Upload ID，用于标识本次Multipart Upload事件。用户可以根据这个ID来发起相关的操作，如中止Multipart Upload、查询Multipart Upload等。
<br>调用MSSClient.initiateMultipartUpload初始化一个分片上传事件：
```
public void initailMultipartExample(String bucketName, String objectName){
    try{
        InitiateMultipartUploadRequest initRequest = new InitiateMultipartUploadRequest(
                bucketName, objectName);
        InitiateMultipartUploadResult initResponse =
                s3client.initiateMultipartUpload(initRequest);
        System.out.println("UploadId " + initResponse.getUploadId());
    }catch (AmazonServiceException ase) {
        //存储服务端处理异常
        System.out.println("Caught an ServiceException.");
        System.out.println("Error Message:    " + ase.getMessage());
        System.out.println("HTTP Status Code: " + ase.getStatusCode());
        System.out.println("Error Code:   " + ase.getErrorCode());
        System.out.println("Error Type:       " + ase.getErrorType());
        System.out.println("Request ID:       " + ase.getRequestId());
    }catch (AmazonClientException ace) {
        //客户端处理异常
        System.out.println("Caught an ClientException.");
        System.out.println("Error Message: " + ace.getMessage());
    }
}
```

###上传分片###
初始化一个Multipart Upload之后，可以根据指定的Object名和Upload ID来分片（Part）上传数据。每一个上传的Part都有一个标识它的号码——分片号。对于同一个Upload ID，该分片号不但唯一标识这一块数据，也标识了这块数据在整个文件内的相对位置。如果你用同一个分片号码，上传了新的数据，那么MSS上已有的这个分片的数据将被覆盖。除了最后一块Part以外，其他的part最小为5M；最后一块Part没有大小限制。每个分片不需要按顺序上传，甚至可以在不同进程、不同机器上上传，MSS会按照分片号排序组成大文件。
<br>调用MSSClient.uploadPart上传分片
```
public void uploadMultipartExample(String bucketName, String objectName, String uploadID, int partNum){
    try{
        // Create request to upload a part.
        String partContent = "part content";
        UploadPartRequest uploadRequest = new UploadPartRequest()
                .withBucketName(bucketName)
                .withKey(objectName)
                .withUploadId(uploadID)
                .withPartNumber(partNum)
                .withInputStream(new ByteArrayInputStream(partContent.getBytes()))
                .withPartNumber(partContent.length());
 
        // Upload part.
        s3client.uploadPart(uploadRequest);
    }catch (AmazonServiceException ase) {
        //存储服务端处理异常
        System.out.println("Caught an ServiceException.");
        System.out.println("Error Message:    " + ase.getMessage());
        System.out.println("HTTP Status Code: " + ase.getStatusCode());
        System.out.println("Error Code:   " + ase.getErrorCode());
        System.out.println("Error Type:       " + ase.getErrorType());
        System.out.println("Request ID:       " + ase.getRequestId());
    }catch (AmazonClientException ace) {
        //客户端处理异常
        System.out.println("Caught an ClientException.");
        System.out.println("Error Message: " + ace.getMessage());
    }
}
```

###完成分片上传###
所有分片上传完成后，需要调用Complete Multipart Upload来完成整个文件的Multipart Upload。在执行该操作时，需要提供所有有效的分片列表（包括分片号和分片ETAG）；MSS收到提交的分片列表后，会逐一验证每个分片的有效性。当所有的数据Part验证通过后，MSS将把这些分片组合成一个完整的Object。
<br>调用MSSClient.completeMultipartUpload完成分片上传：
```
public void completeMultipartExample(String bucketName, String objectName){
    try{
        //partETags用于存储上传完成的每个分片的etag值，在调用分片文件上传完成接口是需要用到
        List<PartETag> partETags = new ArrayList<PartETag>();
        //指定上传的本地大文件
        String filePath = "/opt/bigfile";
         
        //初始化分片上传
        InitiateMultipartUploadRequest initRequest = new InitiateMultipartUploadRequest(
                bucketName, objectName);
        InitiateMultipartUploadResult initResponse =
                s3client.initiateMultipartUpload(initRequest);
         
        //指定分片的大小
        File file = new File(filePath);
        long contentLength = file.length();
        long partSize = 50 * 1024 * 1024; // Set part size to 50 MB.
        long filePosition = 0;
 
        for (int i = 1; filePosition < contentLength; i++) {
            // 最后一个分片可能小于指定的partSize
            partSize = Math.min(partSize, (contentLength - filePosition));
 
            // 创建一个分片上传请求
            UploadPartRequest uploadRequest = new UploadPartRequest()
                    .withBucketName(bucketName).withKey(objectName)
                    .withUploadId(initResponse.getUploadId()).withPartNumber(i)
                    .withFileOffset(filePosition)
                    .withFile(file)
                    .withPartSize(partSize);
 
            // 上传分片，同时将MSS返回的etag值加入数组中
            partETags.add(s3client.uploadPart(uploadRequest).getPartETag());
 
            filePosition += partSize;
        }
         
        //调用分片上传完成接口
        CompleteMultipartUploadRequest compRequest = new
                CompleteMultipartUploadRequest(bucketName,
                objectName,
                initResponse.getUploadId(),
                partETags);
 
        s3client.completeMultipartUpload(compRequest);
    }catch (AmazonServiceException ase) {
        //存储服务端处理异常
        System.out.println("Caught an ServiceException.");
        System.out.println("Error Message:    " + ase.getMessage());
        System.out.println("HTTP Status Code: " + ase.getStatusCode());
        System.out.println("Error Code:   " + ase.getErrorCode());
        System.out.println("Error Type:       " + ase.getErrorType());
        System.out.println("Request ID:       " + ase.getRequestId());
    }catch (AmazonClientException ace) {
        //客户端处理异常
        System.out.println("Caught an ClientException.");
        System.out.println("Error Message: " + ace.getMessage());
    }
}
```

###取消分片上传事件###
该接口可以根据Upload ID中止对应的Multipart Upload事件。当一个Multipart Upload事件被中止后，就不能再使用这个Upload ID做任何操作，已经上传的Part数据也会被删除。
<br>调用MSSClient.abortMultipartUpload取消分片上传事件：
```
public void abortMultipartUploadExample(String bucketName, String objectName, String uploadID){
    try{
        s3client.abortMultipartUpload(new AbortMultipartUploadRequest(
                bucketName, objectName, uploadID));
    }catch (AmazonServiceException ase) {
        //存储服务端处理异常
        System.out.println("Caught an ServiceException.");
        System.out.println("Error Message:    " + ase.getMessage());
        System.out.println("HTTP Status Code: " + ase.getStatusCode());
        System.out.println("Error Code:   " + ase.getErrorCode());
        System.out.println("Error Type:       " + ase.getErrorType());
        System.out.println("Request ID:       " + ase.getRequestId());
    }catch (AmazonClientException ace) {
        //客户端处理异常
        System.out.println("Caught an ClientException.");
        System.out.println("Error Message: " + ace.getMessage());
    }
}
```

###获取所有已上传分片###
```
public void listMultipartUploadExample(String bucketName, String objectName, String uploadID){
    try{
        //创建获取分片的请求
        ListPartsRequest listpartreq = new ListPartsRequest(bucketName, objectName, uploadID);
        //调用获取分片接口，返回分片列表结果
        PartListing listresult = s3client.listParts(listpartreq);
        //输出分片信息
        List<PartSummary> parts_list = listresult.getParts();
        for (int i =0; i < parts_list.size(); i++) {
            PartSummary item = parts_list.get(i);
            //打印分片的etag
            System.out.println("getETag: " + item.getETag());
        }
    }catch (AmazonServiceException ase) {
        //存储服务端处理异常
        System.out.println("Caught an ServiceException.");
        System.out.println("Error Message:    " + ase.getMessage());
        System.out.println("HTTP Status Code: " + ase.getStatusCode());
        System.out.println("Error Code:   " + ase.getErrorCode());
        System.out.println("Error Type:       " + ase.getErrorType());
        System.out.println("Request ID:       " + ase.getRequestId());
    }catch (AmazonClientException ace) {
        //客户端处理异常
        System.out.println("Caught an ClientException.");
        System.out.println("Error Message: " + ace.getMessage());
    }
}
```

###获取Bucket内所有分片上传事件###
列举分片上传事件可以罗列出所有执行中的分片上传事件，即已经初始化的尚未Complete或者Abort的分片上传事件。列举分片上传的可设置的参数如下：
|参数|作用|方法|
|---|---|---|
|Prefix	|限定返回的文件名(object)必须以Prefix作为前缀。注意使用Prefix查询时，返回的文件名(Object)中仍会包含Prefix。|	ListMultipartUploadsRequest.setPrefix(String prefix)|
|Delimiter|	用于对Object名字进行分组的字符。所有名字包含指定的前缀且第一次出现delimiter字符之间的object作为一组元素。|	ListMultipartUploadsRequest.setDelimiter(String delimiter)|

```
public void listMultipartUploadsExample(String bucketName){
    try{
        // 列举分片上传事件
        ListMultipartUploadsRequest listMultipartUploadsRequest = new ListMultipartUploadsRequest(bucketName);
        MultipartUploadListing multipartUploadListing = s3client.listMultipartUploads(listMultipartUploadsRequest);
        for (MultipartUpload multipartUpload : multipartUploadListing.getMultipartUploads()) {
            // Upload Id
            multipartUpload.getUploadId();
            // Key
            multipartUpload.getKey();
            // Date of initiate multipart upload
            multipartUpload.getInitiated();
        }
    }catch (AmazonServiceException ase) {
        //存储服务端处理异常
        System.out.println("Caught an ServiceException.");
        System.out.println("Error Message:    " + ase.getMessage());
        System.out.println("HTTP Status Code: " + ase.getStatusCode());
        System.out.println("Error Code:   " + ase.getErrorCode());
        System.out.println("Error Type:       " + ase.getErrorType());
        System.out.println("Request ID:       " + ase.getRequestId());
        Assert.assertEquals(true, true);
    }catch (AmazonClientException ace) {
        //客户端处理异常
        System.out.println("Caught an ClientException.");
        System.out.println("Error Message: " + ace.getMessage());
    }
}
```


----------


#下载文件#

MSS Java SDK提供了丰富的文件下载接口，用户可以通过以下方式从OSS中下载文件：

 - 流式下载
 - 下载到本地文件
 - 断点续传下载
 - 范围下载

##流式下载
在进行大文件下载时，往往不希望一次性处理全部内容，而是希望流式地处理，一次处理一部分内容。
```
public void getObjectExample(String bucketName, String objectName){
    try{
        //bucketName是桶名
        //objectName是文件名
        S3Object s3object = s3client.getObject(new GetObjectRequest(
                bucketName, objectName));
        InputStream content = s3object.getObjectContent();
        BufferedReader reader = new BufferedReader(new InputStreamReader(content));
        if (content != null) {
                while (true) {
                    String line = reader.readLine();
                    if (line == null) break;
                    System.out.println("\n" + line);
                }
            //获取object后需要close(),释放连接
            s3object.close();
        }
    }catch (AmazonServiceException ase) {
        //存储服务端处理异常
        System.out.println("Caught an ServiceException.");
        System.out.println("Error Message:    " + ase.getMessage());
        System.out.println("HTTP Status Code: " + ase.getStatusCode());
        System.out.println("Error Code:   " + ase.getErrorCode());
        System.out.println("Error Type:       " + ase.getErrorType());
        System.out.println("Request ID:       " + ase.getRequestId());
    }catch (AmazonClientException ace) {
        //客户端处理异常
        System.out.println("Caught an ClientException.");
        System.out.println("Error Message: " + ace.getMessage());
    }catch (Exception e) {
        System.out.println("Error Message: " + e.toString());
    }
}
```

##下载到本地文件##
把Object的内容下载到指定的本地文件中。如果指定的本地文件不存在则会新建。
```
public void getObjectToLocalFileExample(String bucketName, String objectName){
    try{
        s3client.getObject(new GetObjectRequest(bucketName, objectName), new File("/tmp/temfile"));
    }catch (AmazonServiceException ase) {
        //存储服务端处理异常
        System.out.println("Caught an ServiceException.");
        System.out.println("Error Message:    " + ase.getMessage());
        System.out.println("HTTP Status Code: " + ase.getStatusCode());
        System.out.println("Error Code:   " + ase.getErrorCode());
        System.out.println("Error Type:       " + ase.getErrorType());
        System.out.println("Request ID:       " + ase.getRequestId());
    }catch (AmazonClientException ace) {
        //客户端处理异常
        System.out.println("Caught an ClientException.");
        System.out.println("Error Message: " + ace.getMessage());
    }catch (Exception e) {
        System.out.println("Error Message: " + e.toString());
    }
}

```

##范围下载##
如果OSS文件较大，并且只需要其中一部分数据，可以使用范围下载，下载指定范围的数据。如果指定的下载范围是0 - 1000，则返回第0到第1000个字节的数据，包括第1000个，共1001字节的数据，即[0, 1000]。如果指定的范围无效，则传送整个文件。
```
public void getObjectRangeExample(String bucketName, String objectName){
    try{
        GetObjectRequest rangeObjectRequest = new GetObjectRequest(
                bucketName, objectName);
        //设定范围
        rangeObjectRequest.setRange(0, 10);
        S3Object objectPortion = s3client.getObject(rangeObjectRequest);
        InputStream content = objectPortion.getObjectContent();
        BufferedReader reader = new BufferedReader(new InputStreamReader(content));
        if (content != null) {
            while (true) {
                String line = reader.readLine();
                if (line == null) break;
                System.out.println("\n" + line);
            }
            //获取object后需要close(),释放连接
            objectPortion.close();
        }
    }catch (AmazonServiceException ase) {
        //存储服务端处理异常
        System.out.println("Caught an ServiceException.");
        System.out.println("Error Message:    " + ase.getMessage());
        System.out.println("HTTP Status Code: " + ase.getStatusCode());
        System.out.println("Error Code:   " + ase.getErrorCode());
        System.out.println("Error Type:       " + ase.getErrorType());
        System.out.println("Request ID:       " + ase.getRequestId());
    }catch (AmazonClientException ace) {
        //客户端处理异常
        System.out.println("Caught an ClientException.");
        System.out.println("Error Message: " + ace.getMessage());
    }catch (Exception e) {
        System.out.println("Error Message: " + e.toString());
    }
}
```

##限定条件下载##
下载文件时，可以指定一个或多个限定条件，满足限定条件时下载，不满足时报错，不下载文件。可以使用的限定条件如下：
```
public void getObjectWithConstraintExample(String bucketName, String objectName){
    try{
        //如果2010.05.05之后后更新，则下载
        Date DateSinceNow = new Date(2010,5,5);
        GetObjectRequest rangeObjectRequestWithSince = new GetObjectRequest(
                bucketName, objectName);
        rangeObjectRequestWithSince.setModifiedSinceConstraint(DateSinceNow);
        s3client.getObject(rangeObjectRequestWithSince);
 
        //如果2010.05.05之后没有更新，则下载
        GetObjectRequest objectRequesteNotModify = new GetObjectRequest(
                bucketName, objectName);
        objectRequesteNotModify.setUnmodifiedSinceConstraint(DateSinceNow);
        s3client.getObject(objectRequesteNotModify);
 
 
 
        //如果etag匹配，则下载
        String etagStr = "c99a74c555371a433d121f551d6c6392";
        GetObjectRequest objectRequestWithEtag = new GetObjectRequest(
                bucketName, objectName);
        objectRequestWithEtag = objectRequestWithEtag.withMatchingETagConstraint(etagStr);
        s3client.getObject(objectRequestWithEtag);
 
    }catch (AmazonServiceException ase) {
        //存储服务端处理异常
        System.out.println("Caught an ServiceException.");
        System.out.println("Error Message:    " + ase.getMessage());
        System.out.println("HTTP Status Code: " + ase.getStatusCode());
        System.out.println("Error Code:   " + ase.getErrorCode());
        System.out.println("Error Type:       " + ase.getErrorType());
        System.out.println("Request ID:       " + ase.getRequestId());
    }catch (AmazonClientException ace) {
        //客户端处理异常
        System.out.println("Caught an ClientException.");
        System.out.println("Error Message: " + ace.getMessage());
    }catch (Exception e) {
        System.out.println("Error Message: " + e.toString());
    }
}
```


----------


#管理文件#
在MSS中，用户可以通过一系列的接口管理存储空间(Bucket)中的文件(Object)，比如ListObjects，DeleteObject，CopyObject，DoesObjectExist等。Object的名字又称为key或object key。
##Object是否存在##
```
public boolean doesObjectExistExample(String bucketName, String objectName){
    try {
        s3client.getObjectMetadata(bucketName, objectName);
        return true;
    } catch (AmazonS3Exception e) {
        if (e.getStatusCode() == 404) {
            return false;
        }
        throw e;
    }
}
```

##获取文件元信息(Object Meta)##
文件元信息(Object Meta)，是对用户上传到MSS的文件的属性描述，分为两种：HTTP标准属性（HTTP Headers）和User Meta（用户自定义元信息）。 文件元信息可以在各种方式上传或者拷贝文件时进行设置。更多文件元信息的介绍，请参看文件元信息。
``` 
public void getObjectMetaExample(String bucketName, String objectName){
    try{
        GetObjectMetadataRequest req = new GetObjectMetadataRequest(bucketName, objectName);
        ObjectMetadata objectMeta = s3client.getObjectMetadata(req);
        System.out.println(objectMeta.getETag());
        System.out.println(objectMeta.getLastModified());
    }catch (AmazonServiceException ase) {
        //存储服务端处理异常
        System.out.println("Caught an ServiceException.");
        System.out.println("Error Message:    " + ase.getMessage());
        System.out.println("HTTP Status Code: " + ase.getStatusCode());
        System.out.println("Error Code:   " + ase.getErrorCode());
        System.out.println("Error Type:       " + ase.getErrorType());
        System.out.println("Request ID:       " + ase.getRequestId());
    }catch (AmazonClientException ace) {
        //客户端处理异常
        System.out.println("Caught an ClientException.");
        System.out.println("Error Message: " + ace.getMessage());
    }catch (Exception e) {
        System.out.println("Error Message: " + e.toString());
    }
}
```

##列出存储空间中的文件##
ObjectListing的参数如下：
|参数|含义|方法|
|---|----|----|
|Prefix	|本次查询结果的开始前缀。|	String getPrefix()|
|Delimiter|	是一个用于对Object名字进行分组的字符。|	String getDelimiter()|
|Marker|	标明这次List Object的起点。	|String getMarker()|
|MaxKeys|	响应请求内返回结果的最大数目。|	int getMaxKeys()|
|NextMarker	|下一次List Object的起点。|	String getNextMarker()|
|IsTruncated	|指明是否所有的结果都已经返回。	|boolean isTruncated()|
|CommonPrefixes	|如果请求中指定了delimiter参数，则返回的包含CommonPrefixes元素。该元素标明以delimiter结尾，并有共同前缀的object的集合。|	List<String> getCommonPrefixes()|

```
public void getObjectListExample(String bucketName){
    try{
        ListObjectsRequest listObjectsRequest = new ListObjectsRequest()
                .withBucketName(bucketName)
                //查询bucket下固定的前缀
                .withPrefix("photo");
        ObjectListing objectListing = s3client.listObjects(listObjectsRequest);
        for (S3ObjectSummary objectSummary :
                objectListing.getObjectSummaries()) {
            System.out.println(" - " + objectSummary.getKey() + "  " +
                    "(size = " + objectSummary.getSize() +
                    ")");
        }
    }catch (AmazonServiceException ase) {
        //存储服务端处理异常
        System.out.println("Caught an ServiceException.");
        System.out.println("Error Message:    " + ase.getMessage());
        System.out.println("HTTP Status Code: " + ase.getStatusCode());
        System.out.println("Error Code:   " + ase.getErrorCode());
        System.out.println("Error Type:       " + ase.getErrorType());
        System.out.println("Request ID:       " + ase.getRequestId());
    }catch (AmazonClientException ace) {
        //客户端处理异常
        System.out.println("Caught an ClientException.");
        System.out.println("Error Message: " + ace.getMessage());
    }
}
```

##分页所有获取指定前缀的Object##
分页所有获取指定前缀的Object，每页maxKeys条Object。
```
public void getAllObjectsExample(String bucketName){
    try{
        ListObjectsRequest listObjectsRequest = new ListObjectsRequest()
                .withBucketName(bucketName);
        ObjectListing objectListing;
        do {
            objectListing = s3client.listObjects(listObjectsRequest);
            for (S3ObjectSummary objectSummary :
                    objectListing.getObjectSummaries()) {
                System.out.println(" - " + objectSummary.getKey() + "  " +
                        "(size = " + objectSummary.getSize() +
                        ")");
            }
            listObjectsRequest.setMarker(objectListing.getNextMarker());
        } while (objectListing.isTruncated());
    }catch (AmazonServiceException ase) {
        //存储服务端处理异常
        System.out.println("Caught an ServiceException.");
        System.out.println("Error Message:    " + ase.getMessage());
        System.out.println("HTTP Status Code: " + ase.getStatusCode());
        System.out.println("Error Code:   " + ase.getErrorCode());
        System.out.println("Error Type:       " + ase.getErrorType());
        System.out.println("Request ID:       " + ase.getRequestId());
    }catch (AmazonClientException ace) {
        //客户端处理异常
        System.out.println("Caught an ClientException.");
        System.out.println("Error Message: " + ace.getMessage());
    }
}
```

##列出目录下的文件和子目录##
 
```
public void getSubDirAndObjectsExample(String bucketName){
    try{
        ListObjectsRequest listObjectsRequest = new ListObjectsRequest()
                .withBucketName(bucketName);
        //"/" 为文件夹的分隔符
        listObjectsRequest.setDelimiter("/");
        //列出fun目录下的所有文件和文件夹
        listObjectsRequest.setPrefix("photo/");
        ObjectListing objectListing;
        do {
            objectListing = s3client.listObjects(listObjectsRequest);
            for (S3ObjectSummary objectSummary :
                    objectListing.getObjectSummaries()) {
                System.out.println(" - " + objectSummary.getKey() + "  " +
                        "(size = " + objectSummary.getSize() +
                        ")");
            }
            listObjectsRequest.setMarker(objectListing.getNextMarker());
        } while (objectListing.isTruncated());
    }catch (AmazonServiceException ase) {
        //存储服务端处理异常
        System.out.println("Caught an ServiceException.");
        System.out.println("Error Message:    " + ase.getMessage());
        System.out.println("HTTP Status Code: " + ase.getStatusCode());
        System.out.println("Error Code:   " + ase.getErrorCode());
        System.out.println("Error Type:       " + ase.getErrorType());
        System.out.println("Request ID:       " + ase.getRequestId());
    }catch (AmazonClientException ace) {
        //客户端处理异常
        System.out.println("Caught an ClientException.");
        System.out.println("Error Message: " + ace.getMessage());
    }
}
```

##删除文件##
您可以通过MSSClient.deleteObject删除单个文件。
```
public void deleteObjectExample(String bucketName, String objectName){
    try{
        s3client.deleteObject(new DeleteObjectRequest(bucketName, objectName));
    }catch (AmazonServiceException ase) {
        //存储服务端处理异常
        System.out.println("Caught an ServiceException.");
        System.out.println("Error Message:    " + ase.getMessage());
        System.out.println("HTTP Status Code: " + ase.getStatusCode());
        System.out.println("Error Code:   " + ase.getErrorCode());
        System.out.println("Error Type:       " + ase.getErrorType());
        System.out.println("Request ID:       " + ase.getRequestId());
        Assert.assertEquals(true, true);
    }catch (AmazonClientException ace) {
        //客户端处理异常
        System.out.println("Caught an ClientException.");
        System.out.println("Error Message: " + ace.getMessage());
    }
}
```

##拷贝文件##
目前子支持同一账号下的文件之间的copy
```
public void copyObjectExample(String sourceBucketName, String sourceObjectName, String destBucketName, String destObjectName){
    try{
        CopyObjectRequest copyObjRequest = new CopyObjectRequest(
                sourceBucketName, sourceObjectName, destBucketName, destObjectName);
        s3client.copyObject(copyObjRequest);
    }catch (AmazonServiceException ase) {
        //存储服务端处理异常
        System.out.println("Caught an ServiceException.");
        System.out.println("Error Message:    " + ase.getMessage());
        System.out.println("HTTP Status Code: " + ase.getStatusCode());
        System.out.println("Error Code:   " + ase.getErrorCode());
        System.out.println("Error Type:       " + ase.getErrorType());
        System.out.println("Request ID:       " + ase.getRequestId());
    }catch (AmazonClientException ace) {
        //客户端处理异常
        System.out.println("Caught an ClientException.");
        System.out.println("Error Message: " + ace.getMessage());
    }
}
```

##更新文件元信息##
```
public void updateObjectExample(String bucketName,String objectName) {
    try{
        //get origin objectmeta
        GetObjectMetadataRequest req = new GetObjectMetadataRequest(bucketName, objectName);
        ObjectMetadata objectMeta = s3client.getObjectMetadata(req);
        //update meta date
        objectMeta.setHeader("Content-Type","image/png");
 
        //create update request
        CopyObjectRequest copyObjRequest = new CopyObjectRequest(
                bucketName, objectName, bucketName, objectName);
        copyObjRequest.setNewObjectMetadata(objectMeta);
        s3client.copyObject(copyObjRequest);
 
    }catch (AmazonServiceException ase) {
        //存储服务端处理异常
        System.out.println("Caught an ServiceException.");
        System.out.println("Error Message:    " + ase.getMessage());
        System.out.println("HTTP Status Code: " + ase.getStatusCode());
        System.out.println("Error Code:   " + ase.getErrorCode());
        System.out.println("Error Type:       " + ase.getErrorType());
        System.out.println("Request ID:       " + ase.getRequestId());
        Assert.assertEquals(true, true);
    }catch (AmazonClientException ace) {
        //客户端处理异常
        System.out.println("Caught an ClientException.");
        System.out.println("Error Message: " + ace.getMessage());
    }
}
```


----------


#授权访问#
##使用URL签名授权访问##
###生成签名URL###
通过生成签名URL的形式提供给用户一个临时的访问URL。在生成URL时，您可以指定URL过期的时间，从而限制用户长时间访问。
```
public void presignUrlExample(String bucketName, String objectName) {
    try{
        //设定url的有效时间
        java.util.Date expiration = new java.util.Date();
        long milliSeconds = expiration.getTime();
        milliSeconds += 1000 * 60 * 60; // Add 1 hour.
        expiration.setTime(milliSeconds);
 
        //指定授权的bucket和object
        GeneratePresignedUrlRequest generatePresignedUrlRequest =
                new GeneratePresignedUrlRequest(bucketName, objectName);
        //指定授权的请求类型
        generatePresignedUrlRequest.setMethod(HttpMethod.GET);
        generatePresignedUrlRequest.setExpiration(expiration);
 
        //生成授权的url
        URL url = s3client.generatePresignedUrl(generatePresignedUrlRequest);
        System.out.println("Pre-Signed URL = " + url.toString());
 
    }catch (AmazonServiceException ase) {
        //存储服务端处理异常
        System.out.println("Caught an ServiceException.");
        System.out.println("Error Message:    " + ase.getMessage());
        System.out.println("HTTP Status Code: " + ase.getStatusCode());
        System.out.println("Error Code:   " + ase.getErrorCode());
        System.out.println("Error Type:       " + ase.getErrorType());
        System.out.println("Request ID:       " + ase.getRequestId());
    }catch (AmazonClientException ace) {
        //客户端处理异常
        System.out.println("Caught an ClientException.");
        System.out.println("Error Message: " + ace.getMessage());
    }
}
```


----------


#生命周期管理#
MSS允许用户对Bucket设置生命周期规则，以自动淘汰过期掉的文件，节省存储空间。针对不同前缀的文件，用户可以同时设置多条规则。一条规则包含：

 - 规则ID，用于标识一条规则，不能重复
 - 受影响的文件前缀，此规则只作用于符合前缀的文件
 - 过期时间，有三种指定方式：
（1）指定距文件最后修改时间N天过期
（2）指定日期创建前的文件过期，之后的不过期
（3）指定在具体的某一天过期，即在那天之后符合前缀的文件将会过期，而不论文件的最后修改时间。不推荐使用。
 - 是否生效

上面的过期规则对用户上传的文件有效。用户通过uploadPart上传的分片，也可以设置过期规则。Multipart的Lifecycle和文件的类似，过期时间支持1、2两种，不支持3，生效是以init Multipart upload的时间为

##设置生命周期规则##
```
public void putBucketLifecycleExample(String bucketName){
    try{
        BucketLifecycleConfiguration lifecycleConfig = new BucketLifecycleConfiguration();
        List<Rule> lifecycleRules = new ArrayList<Rule>();
        //rule 设置bucket下photo前缀的文件在60天后过期
        Rule ruleOne = new Rule().withId("id1").withExpirationInDays(600).withPrefix("photo").withStatus("Enabled");
        lifecycleRules.add(ruleOne);
        //rule 设置特定日期过期
        Rule ruleTwo = new Rule().withId("id2").withExpirationDate(DateUtils.parseISO8601Date("2022-10-12T00:00:00.000Z")).withPrefix("vedio").withStatus("Enabled");
        lifecycleRules.add(ruleTwo);
        lifecycleConfig.setRules(lifecycleRules);
 
        s3client.setBucketLifecycleConfiguration(bucketName,lifecycleConfig);
    }catch (AmazonServiceException ase) {
        //存储服务端处理异常
        System.out.println("Caught an ServiceException.");
        System.out.println("Error Message:    " + ase.getMessage());
        System.out.println("HTTP Status Code: " + ase.getStatusCode());
        System.out.println("Error Code:   " + ase.getErrorCode());
        System.out.println("Error Type:       " + ase.getErrorType());
        System.out.println("Request ID:       " + ase.getRequestId());
    }catch (AmazonClientException ace) {
        //客户端处理异常
        System.out.println("Caught an ClientException.");
        System.out.println("Error Message: " + ace.getMessage());
    }
}
```

##查看生命周期规则##
```
public void getBucketLifecycleExample(String bucketName){
    try{
        BucketLifecycleConfiguration config = s3client.getBucketLifecycleConfiguration(bucketName);
        if (config != null){
            for (Rule rule : config.getRules()) {
                System.out.println(rule.getId());
                System.out.println(rule.getPrefix());
                System.out.println(rule.getExpirationInDays());
                System.out.println(rule.getExpirationDate());
            }
        }
    }catch (AmazonServiceException ase) {
        //存储服务端处理异常
        System.out.println("Caught an ServiceException.");
        System.out.println("Error Message:    " + ase.getMessage());
        System.out.println("HTTP Status Code: " + ase.getStatusCode());
        System.out.println("Error Code:   " + ase.getErrorCode());
        System.out.println("Error Type:       " + ase.getErrorType());
        System.out.println("Request ID:       " + ase.getRequestId());
    }catch (AmazonClientException ace) {
        //客户端处理异常
        System.out.println("Caught an ClientException.");
        System.out.println("Error Message: " + ace.getMessage());
    }
}
```

##清空生命周期规则##
```
public void deleteBucketLifecycleExample(String bucketName){
    try{
        s3client.deleteBucketLifecycleConfiguration(bucketName);
    }catch (AmazonServiceException ase) {
        //存储服务端处理异常
        System.out.println("Caught an ServiceException.");
        System.out.println("Error Message:    " + ase.getMessage());
        System.out.println("HTTP Status Code: " + ase.getStatusCode());
        System.out.println("Error Code:   " + ase.getErrorCode());
        System.out.println("Error Type:       " + ase.getErrorType());
        System.out.println("Request ID:       " + ase.getRequestId());
    }catch (AmazonClientException ace) {
        //客户端处理异常
        System.out.println("Caught an ClientException.");
        System.out.println("Error Message: " + ace.getMessage());
    }
}
```


----------


#跨域资源共享#
跨域资源共享(CORS)允许web端的应用程序访问不属于本域的资源。MSS提供接口方便开发者控制跨域访问的权限。
##设定CORS规则##
通过setBucketCors 方法将指定的存储空间上设定一个跨域资源共享CORS的规则，如果原规则存在则覆盖原规则。具体的规则主要通过CORSRule类来进行参数设置。代码如下：
```
public void setBucketCorsExample(String bucketName){
    try{
        // setBucketCrossOriginConfiguration(String bucketName, BucketCrossOriginConfiguration bucketCrossOriginConfiguration)
        BucketCrossOriginConfiguration coreConfig = new BucketCrossOriginConfiguration();
        List<CORSRule> corsRules = new ArrayList<CORSRule>();
        //rule 设置cors策略
        CORSRule corRule = new CORSRule();
        ArrayList<String> allowedOrigin = new ArrayList<String>();
        //指定允许跨域请求的来源
        allowedOrigin.add( "http://www.b.com");
        List<AllowedMethods> allowedMethod = new ArrayList<AllowedMethods>();
        //指定允许的跨域请求方法(GET/PUT/DELETE/POST/HEAD)
        allowedMethod.add(AllowedMethods.GET);
        ArrayList<String> allowedHeader = new ArrayList<String>();
        //控制在OPTIONS预取指令中Access-Control-Request-Headers头中指定的header是否允许。
        allowedHeader.add("x-mss-test");
        ArrayList<String> exposedHeader = new ArrayList<String>();
        //指定允许用户从应用程序中访问的响应头
        exposedHeader.add("x-mss-test1");
        corRule.setAllowedMethods(allowedMethod);
        corRule.setAllowedOrigins(allowedOrigin);
        corRule.setAllowedHeaders(allowedHeader);
        corRule.setExposedHeaders(exposedHeader);
        //指定浏览器对特定资源的预取(OPTIONS)请求返回结果的缓存时间,单位为秒。
        corRule.setMaxAgeSeconds(10);
 
        corsRules.add(corRule);
        coreConfig.setRules(corsRules);
        s3client.setBucketCrossOriginConfiguration(bucketName,coreConfig);
 
 
    }catch (AmazonServiceException ase) {
        //存储服务端处理异常
        System.out.println("Caught an ServiceException.");
        System.out.println("Error Message:    " + ase.getMessage());
        System.out.println("HTTP Status Code: " + ase.getStatusCode());
        System.out.println("Error Code:   " + ase.getErrorCode());
        System.out.println("Error Type:       " + ase.getErrorType());
        System.out.println("Request ID:       " + ase.getRequestId());
    }catch (AmazonClientException ace) {
        //客户端处理异常
        System.out.println("Caught an ClientException.");
        System.out.println("Error Message: " + ace.getMessage());
    }
}
```

##获取CORS规则##
我们可以参考存储空间的CORS规则，通过GetBucketCors方法。代码如下：

```
public void  getBucketCorsExample(String bucketName){
    try{
        BucketCrossOriginConfiguration config = s3client.getBucketCrossOriginConfiguration(bucketName);
        if (config != null){
            for (CORSRule rule : config.getRules()) {
                System.out.println(rule.getId());
                System.out.println(rule.getAllowedHeaders());
                System.out.println(rule.getAllowedMethods());
                System.out.println(rule.getAllowedOrigins());
                System.out.println(rule.getExposedHeaders());
                System.out.println(rule.getMaxAgeSeconds());
            }
        }
    }catch (AmazonServiceException ase) {
        //存储服务端处理异常
        System.out.println("Caught an ServiceException.");
        System.out.println("Error Message:    " + ase.getMessage());
        System.out.println("HTTP Status Code: " + ase.getStatusCode());
        System.out.println("Error Code:   " + ase.getErrorCode());
        System.out.println("Error Type:       " + ase.getErrorType());
        System.out.println("Request ID:       " + ase.getRequestId());
    }catch (AmazonClientException ace) {
        //客户端处理异常
        System.out.println("Caught an ClientException.");
        System.out.println("Error Message: " + ace.getMessage());
    }
}
```

##删除CORS规则##
用于关闭指定存储空间对应的CORS并清空所有规则。
```
public void deleteBucketCorsExample(String bucketName){
    try{
        s3client.deleteBucketCrossOriginConfiguration(bucketName);
    }catch (AmazonServiceException ase) {
        //存储服务端处理异常
        System.out.println("Caught an ServiceException.");
        System.out.println("Error Message:    " + ase.getMessage());
        System.out.println("HTTP Status Code: " + ase.getStatusCode());
        System.out.println("Error Code:   " + ase.getErrorCode());
        System.out.println("Error Type:       " + ase.getErrorType());
        System.out.println("Request ID:       " + ase.getRequestId());
    }catch (AmazonClientException ace) {
        //客户端处理异常
        System.out.println("Caught an ClientException.");
        System.out.println("Error Message: " + ace.getMessage());
    }
}
```


