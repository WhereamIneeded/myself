package com.wnkj.utils;

import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.codec.binary.Base64;
import org.apache.log4j.Logger;
import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.Node;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.multipart.commons.CommonsMultipartResolver;

import javax.servlet.ServletRequest;
import javax.servlet.http.HttpServletRequest;
import java.io.*;
import java.math.BigDecimal;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * 自制工具类
 */
public class CommonTools {
    // 使用日志
    private static Logger logger = Logger.getLogger(CommonTools.class);

    // 单个文件上传大小，最大不能超过5M
    private static final int UPLOAD_FILE_MAX_SIZE = 5 * 1024 * 1024;

    // 校验时间的正则表达式
    // 格式：yyyyMMdd
    private static final String CHECK_DATE_FORMAT_REGEX_1 = "^((19|20)[0-9]{2})(((0[13578]|10|12)(0[1-9]|[12][0-9]|3[01]))|((0[469]|11)(0[1-9]|[12][0-9]|3[0]))|((02)(0[1-9]|[12][0-9])))$";
    // 格式：yyyy-MM-dd
    private static final String CHECK_DATE_FORMAT_REGEX_2 = "^((19|20)[0-9]{2})-(((0[13578]|10|12)-(0[1-9]|[12][0-9]|3[01]))|((0[469]|11)-(0[1-9]|[12][0-9]|3[0]))|((02)-(0[1-9]|[12][0-9])))$";
    // 格式：yyyyMMddHHmmss
    private static final String CHECK_DATE_FORMAT_REGEX_3 = "^((19|20)[0-9]{2})(((0[13578]|10|12)(0[1-9]|[12][0-9]|3[01]))|((0[469]|11)(0[1-9]|[12][0-9]|3[0]))|((02)(0[1-9]|[12][0-9])))([01][0-9]|2[0-3])([0-5][0-9])([0-5][0-9])$";
    // 格式：yyyy-MM-dd HH:mm:ss
    private static final String CHECK_DATE_FORMAT_REGEX_4 = "^((19|20)[0-9]{2})-(((0[13578]|10|12)-(0[1-9]|[12][0-9]|3[01]))|((0[469]|11)-(0[1-9]|[12][0-9]|3[0]))|((02)-(0[1-9]|[12][0-9]))) ([01][0-9]|2[0-3]):([0-5][0-9]):([0-5][0-9])$";

    // 用于递归方法中计算递归的层数
    private static int num = 1;

    /**
     * 便捷将Map集合中的数据封装到JavaBean中，字段必须对应！！！
     * @param map
     * @param c
     * @param <T>
     * @return
     */
    public static <T> T mapToBean(Map<String, Object> map, Class<T> c) {
        try {
            // 创建字节码文件的一个实例
            T bean = c.newInstance();
            // 利用BeanUtils工具类将map集合中的数据封装到bean实例中
            BeanUtils.populate(bean, map);
            // 返回实例
            return bean;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    /**
     * 批量处理request中的普通表单项和文件表单项，并返回一个Map集合
     * 该Map集合存储有所有请求参数的值，包括上传文件最终保存文件的文件URL
     * @param request
     * @return
     * @throws Exception
     */
    public static Map<String, Object> uploadFile(HttpServletRequest request) throws Exception {

        logger.info("<<<<<<<<<<<<-------文件上传：开始------->>>>>>>>>>>>");

        // 创建Map集合用于存储结果集或错误信息
        Map<String, Object> resultMap = new HashMap<String, Object>();
        // 定义MultipartHttpServletRequest类型变量
        MultipartHttpServletRequest multipartHttpServletRequest = null;
        /*
            判断传递进来的参数request是不是属于MultipartHttpServletRequest类型
            判断原因：
                1. SpringBoot中，请求在进入Controller之前，如果携带有上传文件，
                    则会自动在过滤器中将HttpServletRequest类型的request解析成MultipartHttpServletRequest类型；
                2. SpringMVC中，则不会自动解析。
                因此需要进行判断，再决定是否能够直接强转类型。
         */
        if (request instanceof MultipartHttpServletRequest) {
            // 如果是，则直接将HttpServletRequest类型的request强制转换为MultipartHttpServletRequest类型
            multipartHttpServletRequest = (MultipartHttpServletRequest) request;
        } else {
            // 如果不是，创建CommonsMultipartResolver类型解析器
            CommonsMultipartResolver commonsMultipartResolver = new CommonsMultipartResolver(request.getSession().getServletContext());
            /*
                判断当前request是不是包含有enctype="multipart/form-data"属性
                判断原因：主要用于区分第一点和第二点。
                    1. 如果是Swagger页面发送的请求，有文件表单项，但没有选择文件上传
                        那么该request判断的时候，不是enctype="multipart/form-data"类型；
                    2. 如果是前端页面发送请求，因为设置了enctype="multipart/form-data"类型，
                        所以该request是enctype="multipart/form-data"类型，因此无影响。
             */
            boolean flag = commonsMultipartResolver.isMultipart(request);
            if (flag) {
                logger.info("CommonTools.java ->> uploadFile() ->> 开始解析request为MultipartHttpServletRequest类型");
                // 解析HttpServletRequest类型的request为MultipartHttpServletRequest类型
                multipartHttpServletRequest = commonsMultipartResolver.resolveMultipart(request);
            }
        }

        // 如果multipartHttpServletRequest等于null，说明request中不包含上传文件
        if (multipartHttpServletRequest == null) {
            logger.info("CommonTools.java ->> uploadFile() ->> multipartHttpServletRequest等于空，说明request中不包含上传文件！");
            // 保存所有普通表单项的请求参数和参数值
            resultMap.putAll(CommonTools.getParameterMap(request));
            // 返回结果集
            return resultMap;
        }

        // 保存所有普通表单项的请求参数和参数值
        resultMap.putAll(CommonTools.getParameterMap(multipartHttpServletRequest));

        // 得到存储有所有MultipartFile文件的Map集合
        Map<String, MultipartFile> multipartFileMap = multipartHttpServletRequest.getFileMap();
        logger.info("CommonTools.java ->> uploadFile() ->> 存储有MultipartFile文件对象的Map集合的长度multipartFileMap.size() = " + multipartFileMap.size());

        // 得到存储有MultipartFile文件的Set集合
        Set<Map.Entry<String, MultipartFile>> set = multipartFileMap.entrySet();

        // 循环遍历得到每一个Entry
        for (Map.Entry<String, MultipartFile> entry : set) {
            // 得到每一个MultipartFile对象
            MultipartFile multipartFile = entry.getValue();

            // 得到文件表单项的name属性的值
            String formFileName = multipartFile.getName();
            logger.info("CommonTools.java ->> uploadFile() ->> 当前上传的文件表单项的name属性值formFileName = " + formFileName);

            // 得到上传文件的原文件名称
            String originalFileName = multipartFile.getOriginalFilename();
            logger.info("CommonTools.java ->> uploadFile() ->> 当前上传文件的原文件名称originalFileName = " + originalFileName);

            // 如果上传文件的原文件名称为空，说明没上传文件
            if (CommonTools.isEmpty(originalFileName)) {
                // 保存对应name属性的属性值为null
                resultMap.put(formFileName, null);
                // 进入下一次循环
                continue;
            }

            // 得到multipartFile文件的大小
            long fileSize = multipartFile.getSize();
            logger.info("CommonTools.java ->> uploadFile() ->> 当前上传的文件表单项的大小fileSize = " + fileSize);
            // 如果超出限定大小，则返回错误信息
            if (fileSize > CommonTools.UPLOAD_FILE_MAX_SIZE) {
                logger.info("<<<<<-----错误：上传的文件大小超过5M----->>>>>");
                logger.info("<<<<<<<<<<<<-------文件上传：结束------->>>>>>>>>>>>");
                resultMap.put("errorMsg", "单个文件表单项大小不得超过5M！原文件名称为" + originalFileName + "的文件表单项已超过5M！");
                return resultMap;
            }

            // 定义字符串存储文件后缀
            String uploadFileSuffix = null;
            // 定义字符串存储上传文件类型标记
            String uploadFileTypeMark = null;

            // 得到上传文件的后缀及文件类型
            Map<String, Object> getFileSuffixAndFileType_Map = CommonTools.getFileSuffixAndFileType(originalFileName);
            // 获取错误信息
            String errorMsg = (String) getFileSuffixAndFileType_Map.get("errorMsg");
            // 如果错误信息不等于空
            if (CommonTools.notEmpty(errorMsg)) {
                logger.info("<<<<<-----错误：" + errorMsg + "----->>>>>");
                logger.info("<<<<<<<<<<<<-------文件上传：结束------->>>>>>>>>>>>");
                resultMap.put("errorMsg", errorMsg);
                return resultMap;
            }
            // 得到上传文件的后缀
            uploadFileSuffix = (String) getFileSuffixAndFileType_Map.get("uploadFileSuffix");
            logger.info("CommonTools.java ->> uploadFile() ->> 当前上传的原文件后缀uploadFileSuffix = " + uploadFileSuffix);
            // 得到上传文件的类型
            uploadFileTypeMark = (String) getFileSuffixAndFileType_Map.get("uploadFileTypeMark");
            logger.info("CommonTools.java ->> uploadFile() ->> 当前上传的原文件类型uploadFileTypeMark = " + uploadFileTypeMark);


            // 定义上传文件的文件类型目录
            String uploadFileTypeDir = null;
            // 如果等于image表示是图片
            if ("image".equals(uploadFileTypeMark)) {
                uploadFileTypeDir = "uploadImages";
            } else { // 否则表示其他类型的文件
                uploadFileTypeDir = "uploadFiles";
            }

            // 得到保存文件的前缀路径
            String saveUploadFilePrefixDirectory = CommonTools.saveUploadFilePrefixDirectory(request, uploadFileTypeDir);

            // 定义保存文件的日期目录
            String saveUploadFileDateDirectory = CommonTools.date2Str(new Date(), "yyyyMMdd");

            // 得到保存文件的完整目录
            File saveUploadFileCompleteDirectory_File = new File(saveUploadFilePrefixDirectory, saveUploadFileDateDirectory);
            // 如果目录不存在的话
            if (! saveUploadFileCompleteDirectory_File.exists()) {
                saveUploadFileCompleteDirectory_File.mkdirs();
            }
            logger.info("CommonTools.java ->> uploadFile() ->> 保存文件的路径 = " + saveUploadFileCompleteDirectory_File.getPath());

            // 定义保存文件的文件名称，带后缀
            String saveUploadFileName = CommonTools.get32UUID() + uploadFileSuffix;
            logger.info("CommonTools.java ->> uploadFile() ->> 保存文件的文件名称 = " + saveUploadFileName);

            // 得到保存文件的最终File类型对象
            File saveFile = new File(saveUploadFileCompleteDirectory_File, saveUploadFileName);
            logger.info("CommonTools.java ->> uploadFile() ->> 保存文件的完整路径 = " + saveFile.getPath());

            // 读取输入流中的文件，并通过输出流保存
            CommonTools.inputReadAndOutputWrite(multipartFile, saveFile);

            // 保存上传文件最终保存的文件URL到map集合中
            resultMap.put(formFileName, saveUploadFileDateDirectory + "/" + saveUploadFileName);
        }

        logger.info("<<<<<<<<<<<<-------文件上传：结束------->>>>>>>>>>>>");
        return resultMap;
    }

    /**
     * 读取输入流，并通过输出流写出
     * @param multipartFile
     * @param file
     * @throws Exception
     */
    private static void inputReadAndOutputWrite(MultipartFile multipartFile, File file) throws Exception {
        // 定义资源输入流用于读取multipartFile对象中的文件输入流
        InputStream uploadFileInput = null;
        // 定义资源输出流，用于将文件输入流写出
        OutputStream saveUploadFileOutput = null;
        try {
            // 得到MultipartFile类型的multipartFile对象中的文件输入流
            uploadFileInput = multipartFile.getInputStream();
            // 关联最终保存文件的File类型对象
            saveUploadFileOutput = new FileOutputStream(file);
            // 定义字节数组，用作缓冲区
            byte[] buf = new byte[1024];
            // 定义int变量记录每次读取到字节数组中的有效长度
            int len = 0;
            // 循环读取
            while ((len = uploadFileInput.read(buf)) != -1) {
                // 写操作
                saveUploadFileOutput.write(buf, 0, len);
            }
            // 刷新
            saveUploadFileOutput.flush();

        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            try {
                if (uploadFileInput != null) {
                    uploadFileInput.close();
                }
                if (saveUploadFileOutput != null) {
                    saveUploadFileOutput.close();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 得到用于保存上传文件的前缀路径
     * @param request
     * @param uploadFileTypeDir
     * @return
     * @throws Exception
     */
    private static String saveUploadFilePrefixDirectory(HttpServletRequest request, String uploadFileTypeDir) throws Exception {
        // 定义资源输入流，专门用于读取path.properties属性文件
        InputStream pathPropertiesInput = null;
        // 定义用于保存文件的前缀路径
        String saveUploadFilePrefixDirectory = null;
        try {
            // 读取类路径下的path.properties属性文件
            pathPropertiesInput = CommonTools.class.getClassLoader().getResourceAsStream("path.properties");

            // 如果资源输入流不为空
            if (pathPropertiesInput != null) {
                // 定义Properties集合用于读取路径资源输入流
                Properties props = new Properties();
                // 把资源输入流中的属性加载到Properties集合中
                props.load(pathPropertiesInput);

                // 如果Properties集合的长度大于0
                if (props.size() > 0) {
                    // 获取其中的filePath属性的值
                    String filePath = props.getProperty("filePath");

                    // 如果filePath属性的值不为空
                    if (CommonTools.notEmpty(filePath)) {

                        // 判断filePath属性的值最后一个字符是否等于 "/"
                        if (filePath.lastIndexOf("/") == (filePath.length() - 1)) {
                            saveUploadFilePrefixDirectory = filePath + uploadFileTypeDir + "/";
                        } else {
                            saveUploadFilePrefixDirectory = filePath + "/" + uploadFileTypeDir + "/";
                        }
                    } else {
                        saveUploadFilePrefixDirectory = request.getServletContext().getRealPath("/upload/" + uploadFileTypeDir + "/");
                    }
                } else {
                    saveUploadFilePrefixDirectory = request.getServletContext().getRealPath("/upload/" + uploadFileTypeDir + "/");
                }
            } else {
                saveUploadFilePrefixDirectory = request.getServletContext().getRealPath("/upload/" + uploadFileTypeDir + "/");
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            try {
                if (pathPropertiesInput != null) {
                    pathPropertiesInput.close();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        return saveUploadFilePrefixDirectory;
    }


    /**
     * 得到HttpServletRequest类型的request中的请求参数，
     * 并存储为Map<String, Object>类型，返回结果集
     * @param request
     * @return
     */
    public static Map<String, Object> getParameterMap(ServletRequest request) throws Exception {
        Map<String, Object> resultMap = new HashMap<String, Object>();
        // 得到存储有所有普通表单项的请求参数Map集合
        Map<String, String[]> paramsMap = request.getParameterMap();

        // 得到存储有普通表单项的请求参数的Set集合
        Set<Map.Entry<String, String[]>> paramsSet = paramsMap.entrySet();
        // 循环遍历得到每一个Entry
        for (Map.Entry<String, String[]> entry : paramsSet) {
            // 得到请求参数的参数名称
            String paramKey = entry.getKey();
            // 得到请求参数的参数值(String数组)
            String[] paramValues = entry.getValue();
            /*
                创建字符串缓冲区
                注：StringBuffer与StringBuilder功能和方法相同
                    1. StringBuffer是线程安全的，但效率相对较慢；
                    2. StringBuilder是线程不安全的，但效率相对较快。
             */
            StringBuilder sb = new StringBuilder();
            // 判断请求参数的参数值，即String数组长度是否等于1
            if (paramValues.length == 1) {
                // 保存到字符串缓冲区
                sb.append(paramValues[0]);
            } else {
                // 循环遍历
                for (int i = 0; i < paramValues.length; i++) {
                    // 保存到字符串缓冲区
                    sb.append(paramValues[i]);
                    // 如果当前循环遍历的索引号不等于数组长度-1
                    if (i != (paramValues.length - 1)) {
                        // 则说明不是最后一个参数值，用逗号隔开
                        sb.append(",");
                    }
                }
            }
            // 调用StringBuffer的toString()方法得到一串字符串
            String paramValue = sb.toString();
            // 用请求参数名称为key，请求参数的值为value保存到Map中
            resultMap.put(paramKey, paramValue);
        }

        return resultMap;
    }

    /**
     * 根据上传的文件判断文件所属类型及返回文件后缀
     * 文件所属类型：图片或其他
     * @param fileName
     * @return
     * @throws Exception
     */
    public static Map<String, Object> getFileSuffixAndFileType(String fileName) throws Exception {

        Map<String, Object> resultMap = new HashMap<String, Object>();

        if (fileName == null || "".equals(fileName) || fileName.trim().isEmpty()) {
            resultMap.put("errorMsg", "参数不能为空！");
            return resultMap;
        }

        int index = fileName.lastIndexOf(".");

        String uploadFileSuffix = null;
        if (index != -1) {
            uploadFileSuffix = fileName.substring(index);
        } else {
            resultMap.put("errorMsg", "该参数不属于文件类型（没有带扩展名）！");
            return resultMap;
        }

        String uploadFileTypeMark = null;
        if (".jpeg".equals(uploadFileSuffix) || ".jpg".equals(uploadFileSuffix)
                || ".png".equals(uploadFileSuffix) || ".gif".equals(uploadFileSuffix)) {
            uploadFileTypeMark = "image";
        } else {
            uploadFileTypeMark = "other";
        }

        resultMap.put("uploadFileSuffix", uploadFileSuffix);
        resultMap.put("uploadFileTypeMark", uploadFileTypeMark);

        return resultMap;
    }


    /**
     * 将Map集合中的数据保存到.properties属性文件中
     * @param propertyMap  存储有属性的Map集合
     * @param propertiesFileAbsolutePath  属性文件的绝对路径
     * @return Map<String, Object>  存储错误信息或成功信息
     *          mark标记：boolean类型，true表成功，false表失败
     *          errorMsg：当且仅当mark为false时有错误信息
     * @throws Exception
     */
    public static Map<String, Object> savePropertyToPropertiesFile(Map<String, String> propertyMap, String propertiesFileAbsolutePath) throws Exception {
        // 创建Map集合用于存储成功信息或错误信息
        Map<String, Object> resultMap = new HashMap<String, Object>();
        // 如果属性文件的路径为空
        if (CommonTools.isEmpty(propertiesFileAbsolutePath)) {
            resultMap.put("mark", false);
            resultMap.put("errorMsg", "参数propertiesFileAbsolutePath不能为空！");
            return resultMap;
        }

        // 判断属性Map集合是否为null
        if (propertyMap == null || propertyMap.size() <= 0) {
            resultMap.put("mark", false);
            resultMap.put("errorMsg", "propertyMap属性集合不能为空！");
            return resultMap;
        }

        // 得到属性文件的File对象
        File propsFile = new File(propertiesFileAbsolutePath);
        // 如果对应路径的文件不存在
        if (! propsFile.exists()) {
            // 得到文件的目录路径
            String directory = propertiesFileAbsolutePath.substring(0, propertiesFileAbsolutePath.lastIndexOf("/"));
            // 创建文件目录的File对象
            File directoryFile = new File(directory);
            // 如果目录不存在
            if (! directoryFile.exists()) {
                // 创建目录
                directoryFile.mkdirs();
            }

            // 创建文件
            propsFile.createNewFile();
        }

        BufferedReader reader = null;
        BufferedWriter writer = null;

        try {
            // 得到属性文件的资源输入流
            reader = new BufferedReader(new InputStreamReader(new FileInputStream(propsFile)));
            // 创建Properties集合用于读取资源输入流中的属性
            Properties props = new Properties();
            // 加载资源输入流中的属性
            props.load(reader);
            System.out.println("before：props.size() = " + props.size());

            // 得到要添加属性到属性文件中的的Set集合
            Set<Map.Entry<String, String>> set = propertyMap.entrySet();
            // 循环遍历
            for (Map.Entry<String, String> entry : set) {
                // 得到属性名称
                String key = entry.getKey();
                // 得到属性值
                String value = (String) entry.getValue();

                System.out.println("key = " + key + "::::" + "value = " + value);
                // 设置属性
                // 如果原先有相同属性名称的属性，旧值会被新值覆盖
                props.setProperty(key, value);
            }

            System.out.println("after：props.size() = " + props.size());
            // 得到属性文件的资源输出流
            writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(propsFile)));

            // 将集合中的属性写入到属性文件中
            props.store(writer, "");

        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            try {
                if (reader != null) {
                    reader.close();
                }
                if (writer != null) {
                    writer.close();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        resultMap.put("mark", true);
        return resultMap;
    }

    /**
     * 读取属性文件中所有的属性
     * @param propertyNameList 属性文件中的属性名称
     * @param propertiesFileAbsolutePath  属性文件的绝对路径
     * @return Map<String, Object>  存储错误信息或成功信息 或 存储属性文件中所有属性名称及属性值
     *      *          mark标记：boolean类型，true表成功，false表失败
     *      *          errorMsg：当且仅当mark为false时有错误信息
     * @throws Exception
     */
    public static Map<String, Object> readPropertyOfPropertiesFile(List<String> propertyNameList, String propertiesFileAbsolutePath) throws Exception {

        logger.info("<<<<<<<<<<<<<<<---------- CommonTools工具类：readPropertyOfPropertiesFile()从属性文件中读取属性值的方法 >> 开始 ---------->>>>>>>>>>>>>>>");

        Map<String, Object> resultMap = new HashMap<String, Object>();

        // 判断要获取的属性值的属性名称集合是否为空
        if (propertyNameList == null || propertyNameList.size() <= 0) {
            logger.info("CommonTools.java ->> readPropertyOfPropertiesFile() ->> 错误操作：存储有要从属性文件中读取属性值的属性名称List集合不能为空！");
            resultMap.put("mark", false);
            resultMap.put("errorMsg", "错误操作：存储有要从属性文件中读取属性值的属性名称List集合不能为空！");
            return resultMap;
        }

        // 判断文件的绝对路径是否为空
        if (CommonTools.isEmpty(propertiesFileAbsolutePath)) {
            logger.info("CommonTools.java ->> readPropertyOfPropertiesFile() ->> 错误操作：要读取的属性文件的路径不能为空！");
            resultMap.put("mark", false);
            resultMap.put("errorMsg", "错误操作：要读取的属性文件的路径不能为空！");
            return resultMap;
        }

        // 得到文件的File对象
        File propsFile = new File(propertiesFileAbsolutePath);
        // 判断路径下的文件是否存在
        if (! propsFile.exists()) {
            logger.info("CommonTools.java ->> readPropertyOfPropertiesFile() ->> 错误操作：指定路径(" + propertiesFileAbsolutePath + ")下的文件不存在！");
            resultMap.put("mark", false);
            resultMap.put("errorMsg", "指定路径(" + propertiesFileAbsolutePath + ")下的文件不存在！");
            return resultMap;
        } else if (propsFile.isDirectory()) { // 判断是不是目录
            logger.info("CommonTools.java ->> readPropertyOfPropertiesFile() ->> 错误操作：根据指定的文件路径(" + propertiesFileAbsolutePath + ")找到的文件不是文件格式！");
            resultMap.put("mark", false);
            resultMap.put("errorMsg", "指定的路径(" + propertiesFileAbsolutePath + ")不是文件格式！");
            return resultMap;
        } else {
            // 得到文件后缀
            String suffix = propertiesFileAbsolutePath.substring(propertiesFileAbsolutePath.lastIndexOf("."));
            // 如果后缀不是.properties
            if (! ".properties".equals(suffix)) {
                logger.info("CommonTools.java ->> readPropertyOfPropertiesFile() ->> 错误操作：指定路径(" + propertiesFileAbsolutePath + ")下的文件后缀必须为.properties！");
                resultMap.put("mark", false);
                resultMap.put("errorMsg", "指定路径(" + propertiesFileAbsolutePath + ")下的文件后缀必须为.properties！");
                return resultMap;
            }
        }

        BufferedReader reader = null;
        try {
            String charset = "UTF-8";
            // 创建资源输入流
            reader = new BufferedReader(new InputStreamReader(new FileInputStream(propsFile), charset));
            // 创建集合加载属性文件中的属性
            Properties props = new Properties();
            // 加载
            props.load(reader);
            // 如果长度大于0
            if (props.size() > 0) {
                // 循环遍历要获取的属性值的属性名称集合
                for (String propertyName : propertyNameList) {
                    // 得到每一个属性的值
                    String propertyValue = props.getProperty(propertyName);
                    // 保存到Map集合中
                    resultMap.put(propertyName, propertyValue);
                }
            }

            logger.info("CommonTools.java ->> readPropertyOfPropertiesFile() ->> 从属性文件中读取得到的Map集合 = " + resultMap);

        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            try {
                if (reader != null) {
                    reader.close();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        resultMap.put("mark", true);

        logger.info("<<<<<<<<<<<<<<<---------- CommonTools工具类：readPropertyOfPropertiesFile()从属性文件中读取属性值的方法 >> 结束 ---------->>>>>>>>>>>>>>>");
        return resultMap;
    }

    /**
     * 得到文件在服务器上的访问地址
     * @param fileName
     * @return
     * @throws Exception
     */
    public static String getAccessPath(HttpServletRequest request, String fileName) throws Exception {
        InputStream input = null;
        try {
            if(fileName == null || "".equals(fileName)) {
                throw new RuntimeException("异常：文件名称不能为空！");
            }

            String path = null;
            input = CommonTools.class.getClassLoader().getResourceAsStream("path.properties");
            if(input != null) {
                Properties props = new Properties();
                props.load(input);

                if(props.size() > 0) {
                    String serverPath = props.getProperty("serverPath");
                    if(serverPath != null && !"".equals(serverPath)) {
                        if(serverPath.lastIndexOf("/") == (serverPath.length() - 1)) {
                            path = serverPath + fileName;
                        } else {
                            path = serverPath + "/" + fileName;
                        }
                    }
                }
            }

            if(path == null) {
                path = request.getContextPath() + "/uploadFiles/uploadImgs/" + fileName;
            }

            return path;

        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            try {
                if(input != null) {
                    input.close();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }


    /**
     * 得到文件/文件夹的完整路径
     *  可以得到在服务器上时的文件/文件夹路径
     *  也可以得到在window下项目中的文件/文件夹路径
     * @param request
     * @param fileName
     * @return
     * @throws Exception
     */
    public static String getAbsolutePath(HttpServletRequest request, String fileName) throws Exception {
        InputStream input = null;
        try {
            if(fileName == null || "".equals(fileName)) {
                throw new RuntimeException("异常：文件名称不能为空！");
            }

            String path = null;
            // 读取属性文件，如果是在服务器上部署，则该文件应该制定有保存文件的路径
            input = CommonTools.class.getClassLoader().getResourceAsStream("path.properties");
            if(input != null) {
                Properties props = new Properties();
                props.load(input);

                if(props.size() > 0) {
                    // 读取属性名为filePath的值
                    String filePath = props.getProperty("filePath");
                    // 如果不为空
                    if(filePath != null && ! "".equals(filePath) && !filePath.trim().isEmpty()) {
                        if(filePath.lastIndexOf("/") != (filePath.length() - 1)) {
                            path = filePath + "/" + fileName;
                        } else {
                            path = filePath + fileName;
                        }
                    }
                }
            }

            String contextPath = null;
            // 如果path值为空，说明配置文件中没有指定路径的值
            if(path == null || "".equals(path)) {
                // 手动指定保存目录
                contextPath = "/uploadFiles/uploadImgs/";
                // 得到保存目录在当前项目中的真实路径
                path = request.getServletContext().getRealPath(contextPath) + fileName;
            }

            // 得到path的File对象
            File file = new File(path);
            // 如果file不存在
            if(! file.exists()) {
                throw new RuntimeException("异常：文件在路径下(" + path + ")找不到！");
            }

            return file.getAbsolutePath();

        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            try {
                if(input != null) {
                    input.close();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 得到某个属性文件在classes目录下的绝对路径
     * @param propertiesFileName
     * @return
     * @throws Exception
     */
    public static String getAbsolutePath(String propertiesFileName) throws Exception {

        if (CommonTools.isEmpty(propertiesFileName)) {
            throw new RuntimeException("参数propertiesFileName不能为空！");
        }

        URL url = CommonTools.class.getClassLoader().getResource(propertiesFileName);

        String propertiesFileAbsolutePath = null;
        if (url != null) {
            propertiesFileAbsolutePath = url.getPath();
        }

        return propertiesFileAbsolutePath;
    }


    /**
     * 删除文件或文件夹
     * @param file
     */
    public static void deleteFile(File file) {

        // 判断文件是否存在
        if(! file.exists()) {
            throw new RuntimeException("异常：路径对应的文件不存在！");
        }
        // 判断file是不是文件夹类型
        if(file.isDirectory()) {
            // 得到文件夹下面的所有文件夹及文件的File对象
            File[] files = file.listFiles();
            // 循环遍历每个File
            for(int i = 0; i < files.length; i++) {
                // 递归
                deleteFile(files[i]);
            }
            // 删除文件夹
            file.delete();
        } else { // 如果不是则表示属于文件
            // 得到当前文件的父目录
            File parentFile = file.getParentFile();
            // 得到所有的子文件
            File[] childFiles = parentFile.listFiles();
            // 判断父目录下的子文件个数是否等于1
            if(childFiles.length == 1) {
                // 删除子文件
                file.delete();
                // 删除父目录
                parentFile.delete();
            } else {
                // 否则说明父目录仍有多个子文件，所以直接删除文件即可
                file.delete();
            }
        }
    }


    /**
     * 根据base64格式字符串得到图片文件主数据和后缀
     * @param base64Data  base64格式的数据
     * @return
     * @throws Exception
     */
    public static Map<String, String> getDataAndSuffix(String base64Data, String suffixKey, String bodyKey) throws Exception {
        // 如果base64字符串数据为空，则抛出异常
        if(base64Data == null || "".equals(base64Data)) {
            throw new RuntimeException("异常：base64数据不能为空！");
        }

        String prefix = null;
        String dataBody = null;
        // 如果base64字符串数据中包含有base64则进行切割
        if(base64Data.contains("base64,")) {
            // 切割，正常是得到两个子串
            String[] strArr = base64Data.split("base64,");
            // 如果数组长度为2，说明数据正常
            if(strArr.length == 2) {
                // 将带图片格式的子串赋值给prefix
                prefix = strArr[0];
                // 将图片数据的子串赋值给dataBody
                dataBody = strArr[1];
            } else {
                throw new RuntimeException("异常：base64数据格式错误！");
            }
        }

        // 判断上传图片的后缀
        String suffix = null;
        if(prefix != null) {
            if ("data:image/jpeg;".equalsIgnoreCase(prefix)) {
                suffix = ".jpg";
            } else if ("data:image/jpg;".equalsIgnoreCase(prefix)) {
                suffix = ".jpg";
            } else if ("data:image/png;".equalsIgnoreCase(prefix)) {
                suffix = ".png";
            } else if ("data:image/gif;".equalsIgnoreCase(prefix)) {
                suffix = ".gif";
            } else {
                throw new RuntimeException("异常：图片格式未知！");
            }
        }

        Map<String, String> map = new HashMap<String, String>();

        map.put(suffixKey, suffix);
        map.put(bodyKey, dataBody);

        return map;
    }

    /**
     * 得到保存文件的File对象
     * @param request
     * @param suffix   文件的后缀
     * @return
     * @throws Exception
     */
    public static File getSaveFile(HttpServletRequest request, String suffix) throws Exception {
        InputStream input = null;
        try {
            // 得到当前时间，用作保存图片的日期目录
            Date date = new Date();
            // 转为字符串
            String createtime = CommonTools.date2Str(date, "yyyy-MM-dd");
            String dir = createtime.replace("-", "");


            String path = null;
            File file = null;
            // 读取属性文件
            input = CommonTools.class.getClassLoader().getResourceAsStream("path.properties");
            if(input != null) {
                Properties props = new Properties();
                // 加载资源输入流中的属性数据
                props.load(input);

                if(props.size() > 0) {
                    // 读取属性名为filePath的值
                    String filePath = props.getProperty("filePath");
                    // 如果不为空
                    if(filePath != null && ! "".equals(filePath) && ! filePath.trim().isEmpty() ) {
                        // 判断最后一个字符是不是斜杠
                        if(filePath.lastIndexOf("/") == (filePath.length() - 1)) {
                            // 如果是直接拼接日期目录
                            path = filePath + dir;
                        } else {
                            // 如果不是则补充斜杠再拼接日期目录
                            path = filePath + "/" + dir;
                        }
                        // 得到路径的File对象
                        file = new File(path);
                        // 如果目录不存在则创建目录
                        if(! file.exists()) {
                            file.mkdirs();
                        }
                    }
                }
            }

            String contextPath = null;
            // 如果path值为空，说明配置文件中没有指定路径的值
            if(path == null || "".equals(path)) {
                // 手动指定保存目录
                contextPath = "/uploadFiles/uploadImgs/" + dir;
                // 得到保存目录在当前项目中的真实路径
                path = request.getServletContext().getRealPath(contextPath);
                // 得到路径的File对象
                file = new File(path);
                // 如果目录不存在则创建目录
                if(! file.exists()) {
                    file.mkdirs();
                }
            }

            // 定义保存文件的文件名称
            String imgName = CommonTools.get32UUID() + suffix;
            // 利用目录和文件名称创建最终保存图片的File对象
            File saveFile = new File(file, imgName);

            return saveFile;
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            try {
                if(input != null) {
                    input.close();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }


    /**
     * 保存文件，并返回日期文件夹及文件名称拼接的字符串
     * @param saveFile
     * @param dataBody  base64格式的数据体（即除去类似data:image/jpeg;base64,的开头的数据体）
     * @return
     * @throws Exception
     */
    public static String saveImg(File saveFile, String dataBody) throws Exception {
        FileOutputStream output = null;
        try {
            if(dataBody == null) {
                throw new RuntimeException("异常：base64格式数据主体不能为空！");
            }
            // 将base64数据主体转换成字节数组
            byte[] bytes = Base64.decodeBase64(dataBody.getBytes("utf-8"));
            // 调整异常数据
            for(int i = 0; i < bytes.length; i++) {
                if(bytes[i] < 0) {
                    bytes[i] += 256;
                }
            }
            // 创建字节输出流
            output = new FileOutputStream(saveFile);
            // 写操作
            output.write(bytes);
            // 刷新
            output.flush();

            // 得到当前时间，用作保存图片的日期目录
            Date date = new Date();
            // 转为字符串
            String createtime = CommonTools.date2Str(date, "yyyy-MM-dd");
            String dir = createtime.replace("-", "");

            // 得到带日期目录和文件名称的字符串
            String result = dir + "/" + saveFile.getName();
            // 作为图片URL返回
            return result;
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            try {
                if(output != null) {
                    output.close();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 按照yyyy-MM-dd HH:mm:ss的默认格式，将日期格式化为字符串
     * @param date
     * @return
     */
    public static String date2Str(Date date) {
        return date2Str(date, "yyyy-MM-dd HH:mm:ss");
    }

    /**
     * 按照参数format的格式，将日期格式化为字符串
     * @param date
     * @param format
     * @return
     */
    public static String date2Str(Date date, String format) {
        if(date != null) {
            SimpleDateFormat sdf = new SimpleDateFormat(format);
            return sdf.format(date);
        } else {
            return "";
        }
    }

    /**
     * 得到32位随机字符的ID
     * @return
     */
    public static String get32UUID() {
        return UUID.randomUUID().toString().replace("-", "").toUpperCase();
    }

    /**
     * 生成指定长度的随机字符码
     * @param length
     * @return
     */
    public static String createRandomCode(int length) {
        // 随机字符组成的字符串
        String base = "0123456789abcdefghijklmnopqrstuvwxyz";
        // 用于产生随机字符的对象
        Random random = new Random();
        // 字符串缓冲区
        StringBuffer sb = new StringBuffer();
        for(int i = 0; i < length; i++) {
            // 从随机字符串中得到某一个字符的下标
            int index = random.nextInt(base.length());
            // 得到下标对应的字符
            char c = base.charAt(index);
            // 保存到字符串缓冲区
            sb.append(c);
        }
        return sb.toString().toUpperCase();
    }

    /**
     * 判断字符串是否为空
     * @param text
     * @return
     */
    public static boolean isEmpty(String text) {
        return text == null || "".equals(text) || text.trim().isEmpty();
    }

    /**
     * 判断字符串是否不为空
     * @param text
     * @return
     */
    public static boolean notEmpty(String text) {
        return text != null && ! "".equals(text) && ! text.trim().isEmpty();
    }


    /**
     * 除法运算得到百分比
     * @param diviend 被除数
     * @param divisor 除数
     * @param reservedDigits 保留位数
     * @return
     */
    public static String divideGetPercentage(int diviend, int divisor, int reservedDigits) {
        // 将被除数转为BigDecimal类型
        BigDecimal diviend_bigDecimal = new BigDecimal(diviend);
        // 将除数转为BigDecimal类型
        BigDecimal divisor_bigDecimal = new BigDecimal(divisor);
        // 执行除法运算，由被除数调用divide()方法，方法中第一个参数为除数
        BigDecimal remainder_bigDecimal = diviend_bigDecimal.divide(divisor_bigDecimal, 4, BigDecimal.ROUND_HALF_UP);
        // 将余数乘以100，再除以1，格式化保留位数
        BigDecimal val = remainder_bigDecimal.multiply(new BigDecimal(100)).divide(new BigDecimal(1), reservedDigits, BigDecimal.ROUND_HALF_UP);
        // 得到转为字符串拼接%符号百分比
        return val.toString() + "%";
    }

    /**
     * 发送POST请求
     * @param url
     * @param param
     * @param contentType
     * @param encode
     * @return
     * @throws Exception
     */
    public static String sendPost(String url, String param, String contentType, String encode) throws Exception {

        logger.info("<<<<<<<<<<<<<<<---------- CommonTools工具类：sendPost()发送POST请求接口 >> 开始 ---------->>>>>>>>>>>>>>>");

        if (CommonTools.isEmpty(url)) {
            throw new RuntimeException("异常：请求URL不能为空！");
        }
        logger.info("CommonTools.java ->> sendPost() ->> 发送POST请求的URL = " + url);

        logger.info("CommonTools.java ->> sendPost() ->> 发送POST请求的参数param = " + param);

        if (CommonTools.isEmpty(contentType)) {
            contentType = "application/x-www-form-urlencoded";
//            contentType = "application/json";
        }
        logger.info("CommonTools.java ->> sendPost() ->> 发送POST请求的请求头Content-Type = " + contentType);

        if (CommonTools.isEmpty(encode)) {
            encode = "UTF-8";
        }
        logger.info("CommonTools.java ->> sendPost() ->> 发送POST请求的编码类型encode = " + encode);

        HttpURLConnection httpURLConnection = null;
        BufferedReader in = null;
        BufferedWriter out = null;
        String result = null;
        try {
            URL httpUrl = new URL(url);
            httpURLConnection = (HttpURLConnection) httpUrl.openConnection();
            httpURLConnection.setDoInput(true);
            httpURLConnection.setDoOutput(true);
            httpURLConnection.setRequestMethod("POST");
            httpURLConnection.setConnectTimeout(20000);
            httpURLConnection.setReadTimeout(60000);
            httpURLConnection.setRequestProperty("accept", "*/*");
            httpURLConnection.setRequestProperty("connection", "Keep-Alive");
            httpURLConnection.setRequestProperty("Content-Type", contentType + ";" + encode);

            out = new BufferedWriter(new OutputStreamWriter(httpURLConnection.getOutputStream(), encode));
            out.write(param);
            out.flush();

            in = new BufferedReader(new InputStreamReader(httpURLConnection.getInputStream(), encode));

            if (httpURLConnection.getResponseCode() == 200) {

                StringBuilder sb = new StringBuilder();

                String line = null;
                while ((line = in.readLine()) != null) {
                    sb.append(line);
                }

                result = sb.toString();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            try {
                if (in != null) {
                    in.close();
                }
                if (out != null) {
                    out.close();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        logger.info("CommonTools.java ->> sendPost() ->> 发送POST请求的得到的结果result = " + result);

        logger.info("<<<<<<<<<<<<<<<---------- CommonTools工具类：sendPost()发送POST请求接口 >> 结束 ---------->>>>>>>>>>>>>>>");

        return result;
    }

    /**
     * 对字符串进行MD5摘要加密
     * @param data // 请求参数
     * @return String // 加密签名
     */
    public static String getMD5 (String data) {

        logger.info("<<<<<<<<<<<<<<<---------- CommonTools工具类：getMD5()信息摘要加密方法 >> 开始 ---------->>>>>>>>>>>>>>>");

        if (CommonTools.isEmpty(data)) {
            throw new RuntimeException("用于MD5信息摘要加密的参数data不能为空！");
        }

        String sign = null;
        try {
            // 创建MD5报文摘要实例
            MessageDigest md = MessageDigest.getInstance("MD5");
            // 更新数据到MD5报文摘要实例中
            md.update(data.getBytes("UTF-8"));
            // 得到MD5报文摘要实例中数据并存储为字节数组
            byte[] bytesArr = md.digest();

            int num = 0;
            // 创建字符串缓冲区
            StringBuilder sb = new StringBuilder();
            // 循环遍历字节数组
            for (int i = 0; i < bytesArr.length; i++) {
                // 得到每一个字节
                num = bytesArr[i];

                // 矫正错误数据
                if (num < 0) {
                    num += 256;
                }
                // 如果num小于16
                if (num < 16) {
                    sb.append("0");
                }
                // 得到对应数字的16进制字符串
                sb.append(Integer.toHexString(num));
            }

            sign = sb.toString().toUpperCase();


        } catch (Exception e) {
            e.printStackTrace();
        }

        logger.info("<<<<<<<<<<<<<<<---------- CommonTools工具类：getMD5()信息摘要加密方法 >> 结束 ---------->>>>>>>>>>>>>>>");

        return sign;
    }

    /**
     * Base64加密
     * @param data
     * @return
     * @throws Exception
     */
    public static String encodeBase64 (String data) throws Exception {
        logger.info("<<<<<<<<<<<<<<<---------- CommonTools工具类：encodeBase64()对数据进行base64加密 >> 开始 ---------->>>>>>>>>>>>>>>");

        if (CommonTools.isEmpty(data)) {
            logger.info("CommonTools.java ->> encodeBase64() ->> 要进行base64加密的参数data不能为空！");
            throw new RuntimeException("要进行base64加密的参数data不能为空！");
        }

        String charset = "UTF-8";

        byte[] verificationTextByte = Base64.encodeBase64(data.getBytes(charset));

        String result = new String(verificationTextByte, charset);

        logger.info("CommonTools.java ->> encodeBase64() ->> Base64加密的结果 = " + result);

        logger.info("<<<<<<<<<<<<<<<---------- CommonTools工具类：encodeBase64()对数据进行base64加密 >> 开始 ---------->>>>>>>>>>>>>>>");

        return result;
    }

    /**
     * Base64解密
     * @param data
     * @return
     * @throws Exception
     */
    public static String decodeBase64 (String data) throws Exception {
        logger.info("<<<<<<<<<<<<<<<---------- CommonTools工具类：decodeBase64()对数据进行base64解密 >> 开始 ---------->>>>>>>>>>>>>>>");

        if (CommonTools.isEmpty(data)) {
            logger.info("CommonTools.java ->> decodeBase64() ->> 要进行base64解密的参数data不能为空！");
            throw new RuntimeException("要进行base64解密的参数data不能为空！");
        }

        String charset = "UTF-8";

        byte[] verificationTextByte = Base64.decodeBase64(data.getBytes(charset));

        String result = new String(verificationTextByte, charset);

        logger.info("CommonTools.java ->> decodeBase64() ->> Base64解密的结果 = " + result);

        logger.info("<<<<<<<<<<<<<<<---------- CommonTools工具类：decodeBase64()对数据进行base64解密 >> 开始 ---------->>>>>>>>>>>>>>>");

        return result;
    }


    /**
     * 批量校验参数是否为空，若其中一个为空，则直接返回
     * @param parametersMap
     * @return
     * @throws Exception
     */
    public static Map<String, Object> batchCheckParameterIsEmpty(Map<String, Object> parametersMap) throws Exception {

        logger.info("<<<<<<<<<<<<<<<---------- CommonTools工具类：batchCheckParameterIsEmpty()批量校验参数是否为空的方法 >> 开始 ---------->>>>>>>>>>>>>>>");

        Map<String, Object> resultMap = new HashMap<String, Object>();

        if (parametersMap == null) {
            logger.info("CommonTools.java ->> batchCheckParameterIsEmpty() ->> 批量校验参数是否为空报错：参数Map集合不能为空！");
            resultMap.put("mark", false);
            resultMap.put("errorMsg", "批量校验参数是否为空报错：参数Map集合不能为空！");
            return resultMap;
        }

        Set<Map.Entry<String, Object>> set = parametersMap.entrySet();

        for (Map.Entry<String, Object> entry : set) {
            String key = entry.getKey();
            String value = (String) entry.getValue();

            if (CommonTools.isEmpty(value)) {
                logger.info("CommonTools.java ->> batchCheckParameterIsEmpty() ->> 批量校验参数是否为空报错：：参数名称为" + key + "的参数值不能为空！");
                resultMap.put("mark", false);
                resultMap.put("errorMsg", "批量校验参数是否为空报错：参数名称为" + key + "的参数值不能为空！");
                return resultMap;
            }
        }

        resultMap.put("mark", true);
        resultMap.put("errorMsg", "");

        logger.info("<<<<<<<<<<<<<<<---------- CommonTools工具类：batchCheckParameterIsEmpty()批量校验参数是否为空的方法 >> 结束 ---------->>>>>>>>>>>>>>>");

        return resultMap;
    }

    /**
     * 校验日期字符串格式
     * @param date
     * @param format 只允许为：yyyyMMdd 或 yyyy-MM-dd 或 yyyyMMddHHmmss 或 yyyy-MM-dd HH:mm:ss
     * @return
     */
    public static boolean checkDateFormat(String date, String format) {

        logger.info("<<<<<<<<<<<<<<<---------- CommonTools工具类：checkDateFormat()校验日期字符串格式的方法 >> 开始 ---------->>>>>>>>>>>>>>>");

        if (CommonTools.isEmpty(date)) {
            logger.info("CommonTools.java ->> checkDateFormat() ->> 校验日期字符串格式方法的参数date不允许为空！");
            throw new RuntimeException("校验日期字符串格式方法的参数date不允许为空！");
        }

        if (CommonTools.isEmpty(format)) {
            logger.info("CommonTools.java ->> checkDateFormat() ->> 校验日期字符串格式方法的参数format不允许为空！");
            throw new RuntimeException("校验日期字符串格式方法的参数format不允许为空！");
        } else if (! "yyyyMMdd".equals(format) && ! "yyyy-MM-dd".equals(format)
                && ! "yyyyMMddHHmmss".equals(format) && ! "yyyy-MM-dd HH:mm:ss".equals(format)) {
            logger.info("CommonTools.java ->> checkDateFormat() ->> 校验日期字符串格式方法的参数format指定的校验格式错误（只允许yyyyMMdd或yyyy-MM-dd或yyyyMMddHHmmss或yyyy-MM-dd HH:mm:ss）");
            throw new RuntimeException("校验日期字符串格式方法的参数format指定的校验格式错误（只允许yyyyMMdd或yyyy-MM-dd或yyyyMMddHHmmss或yyyy-MM-dd HH:mm:ss）");
        }

        String regex = null;
        if ("yyyyMMdd".equals(format)) {
            regex = CHECK_DATE_FORMAT_REGEX_1;
        } else if ("yyyy-MM-dd".equals(format)) {
            regex = CHECK_DATE_FORMAT_REGEX_2;
        } else if ("yyyyMMddHHmmss".equals(format)) {
            regex = CHECK_DATE_FORMAT_REGEX_3;
        } else {
            regex = CHECK_DATE_FORMAT_REGEX_4;
        }

        logger.info("CommonTools.java ->> checkDateFormat() ->> 即将要进行校验的日期字符串date = " + date);
        logger.info("CommonTools.java ->> checkDateFormat() ->> 校验日期字符串的格式format = " + format);

        boolean result = date.matches(regex);
        logger.info("CommonTools.java ->> checkDateFormat() ->> 校验日期字符串格式的结果 = " + result);

        logger.info("<<<<<<<<<<<<<<<---------- CommonTools工具类：checkDateFormat()校验日期字符串格式的方法 >> 结束 ---------->>>>>>>>>>>>>>>");
        return result;
    }

    /**
     * 得到XML格式字符串中的所有标签节点数据
     * @param xmlString
     * @return
     * @throws Exception
     */
    public static Map<String, Object> getAllElementOfXmlString (String xmlString) throws Exception {
        logger.info("<<<<<<<<<<<<<<<---------- CommonTools工具类：getAllElementOfXmlString()得到XML格式字符串中所有标签节点数据方法 >> 开始 ---------->>>>>>>>>>>>>>>");
        // 解析Xml格式的字符串得到Dom文档对象
        Document document = DocumentHelper.parseText(xmlString);
        // 得到根节点
        Element rootElement = document.getRootElement();
        logger.info("CommonTools.java ->> recursiveGetElement() ->> xml格式的字符串xmlString的根元素节点名称 = " + rootElement.getName());
        // 递归得到所有的标签节点数据
        Map<String, Object> resultMap = recursiveGetElement(rootElement);
        logger.info("CommonTools.java ->> recursiveGetElement() ->> xml格式中所有的元素节点数据组成的Map集合 = " + resultMap);
        logger.info("<<<<<<<<<<<<<<<---------- CommonTools工具类：getAllElementOfXmlString()得到XML格式字符串中所有标签节点数据方法 >> 结束 ---------->>>>>>>>>>>>>>>");
        return resultMap;
    }

    /**
     * 递归获取节点中的节点名称以及节点的文本内容
     * @param node
     * @return
     */
    public static Map<String, Object> recursiveGetElement (Node node) {
        Map<String, Object> resultMap = new HashMap<String, Object>();

        Element element = (Element) node;
        logger.info("CommonTools.java ->> recursiveGetElement() ->> 当前操作的节点的名称 = " + element.getName());
        List<Element> childElementList = element.elements();
        if (childElementList.size() > 0) {
            resultMap.put(element.getName(), "");
            for (Element childElement : childElementList) {
                resultMap.putAll(recursiveGetElement(childElement));
            }
        } else {
            logger.info("CommonTools.java ->> recursiveGetElement() ->> 当前操作的节点的值 = " + element.getText());
            resultMap.put(element.getName(), element.getText());
        }

        logger.info("CommonTools.java ->> recursiveGetElement() ->> 最后得到的Map集合 = " + resultMap);
        return resultMap;
    }

}
