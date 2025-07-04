# LibreOfficeAndroid

LibreOffice 是一款功能强大的办公软件，支持 ODF, *.docx, *.xlsx, *.pptx 等各类文档离线浏览，LibreOfficeAndroid为可直接运行的版本。

## 实现功能：
+ 支持odf、pdf、word、powerpoint、excel、publisher、openxmlformats、csv、keynote等文档查看；
+ 支持jpg、png、svg等各类图片查看；
+ 支持odg、otg、vsd、svm、svg等绘图软件查看。

## 已知问题：
* 项目中so资源`liblo-native-code.so`较大，无法上传到Github:  
  GitHub文件大小限制，超过100M文件无法直接上传到代码资源库，因此该so文件采用压缩上传的方式，clone项目后，需要先将该so文件解压到/app/libs文件夹下对应的架构模块中，然后再编译项目。

## 开发环境：
> Android SDK: minSdk 23, [app/build.gradle](./app/build.gradle)  
> 第三方库: [build.gradle](./build.gradle)  
> JDK: OpenJDK version "21.0.6"

## 构建项目：
### 1. 克隆此项目
```sh
git clone git@github.com:gurecn/LibreOfficeAndroid.git
```
### 2. 导入Android Studio
建议使用最新、稳定版本，本人使用`Android Studio Iguana | 2023.2.1 Patch 1`版本，按照常规项目导入即可，`Android Studio`会自动安装并配置 Android 开发环境。  
**配置完成后，需要关注`已知问题`，在编译执行项目前，解压`liblo-native-code.so`文件。**


## 联系作者：
访问我的资源: <a href="https://github.com/gurecn">https://github.com/gurecn</a>  

给我发送邮箱：[gurecn@163.com](mailto:gurecn@163.com)


