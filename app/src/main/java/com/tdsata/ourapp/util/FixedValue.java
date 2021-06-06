package com.tdsata.ourapp.util;

/**
 * 项目中的一些常量值的集合.
 */
public class FixedValue {
    //======================服务器========================
    /**
     * 服务器访问地址.
     * 若为http，请在AndroidManifest.xml文件中设置usesCleartextTraffic属性为true.
     */
    public static final String address = "https://域名:端口号";

    /**
     * 服务器RSA密钥对定时更新时间间隔.
     */
    public static final int rsaUpdateTime = 600000/*10分钟*/;

    //=====================配置文件=======================
    /**
     * 登录配置文件名.
     */
    public static final String LoginCfg = "loginCfg";

    /**
     * 主页配置文件名.
     */
    public static final String HomeCfg = "homeCfg";

    /**
     * 搜索配置文件名.
     */
    public static final String SearchCfg = "searchCfg";

    /**
     * 后台服务配置文件.
     */
    public static final String PullServiceCfg = "serviceCfg";

    //====================配置文件目录=====================
    /**
     * 签到数据的导出目录.
     */
    public static final String exportSignInDirectory = "/SignInData";

    /**
     * 头像图片的缓存路径.
     */
    public static final String photoDirectory = "/HeadPhoto";

    //=======================键名=========================
    /**
     * 登录配置文件中存储是否自动登录的键.
     */
    public static final String autoLogin = "autoLogin";

    /**
     * 存储头像缓存文件名的键.
     */
    public static final String headPhoto = "headPhoto";

    /**
     * 文件存储中存储部门时的键，同时也是Intent数据传递中传递部门枚举的键.
     */
    public static final String myDepartment = "department";

    /**
     * 文件存储中存储账号时的键，同时也是Intent数据传递中传递账号的键.
     */
    public static final String myAccount = "account";

    /**
     * 文件存储中存储密码时的键.
     */
    public static final String myPassword = "password";

    /**
     * 传递当前的签到活动的键.
     */
    public static final String currentSignIn = "currentSignInActivity";

    /**
     * Intent中传递部员对象的键.
     */
    public static final String currentMember = "currentMember";

    /**
     * 签到数据的缓存目录.
     */
    public static final String signInDataDirectory = "/SignInDataCache";

    /**
     * 传递裁剪的Bitmap的键.
     */
    public static final String cropBitmap = "cropBitmap";

    /**
     * 传递裁剪的图片的Uri的键.
     */
    public static final String cropUri = "cropUri";

    /**
     * 传递裁剪结果的键.
     */
    public static final String cropResult = "cropResult";

    //=======================其它=========================
    /**
     * 广播讯息过滤.
     */
    public static final String MY_ACTION_NAME = "PULL_MESSAGE_IN_TIME";

    /**
     * 设置首页图片轮播的间隔时间.
     */
    public static final long carouselIntervals = 3000L;
}
