package com.wj.jd.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.drawable.Drawable
import android.text.TextUtils
import android.view.View
import android.widget.RemoteViews
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.SimpleTarget
import com.bumptech.glide.request.transition.Transition
import com.google.gson.Gson
import com.wj.jd.MyApplication
import com.wj.jd.R
import com.wj.jd.bean.JingDouBean
import com.wj.jd.bean.RedPacket
import com.wj.jd.bean.UserBean
import com.wj.jd.util.BitmapUtil
import com.wj.jd.util.CacheUtil.getString
import com.wj.jd.util.HttpUtil
import com.wj.jd.util.StringCallBack
import com.wj.jd.util.TimeUtil
import com.wj.jd.util.TimeUtil.getCurrentData
import com.wj.jd.util.TimeUtil.getCurrentHH
import com.wj.jd.util.TimeUtil.parseTime
import org.json.JSONObject
import java.lang.Exception

/**
 * author wangjing
 * Date 2021/10/13
 * Description
 */
object WidgetUpdateDataUtil {
    var remoteViews: RemoteViews? = null
    var gson = Gson()
    var page = 1
    var todayTime: Long = 0
    var yesterdayTime: Long = 0

    fun updateWidget() {
        HttpUtil.cancelAll()

        remoteViews = RemoteViews(MyApplication.mInstance.getPackageName(), R.layout.widges_layout)
        val manager = AppWidgetManager.getInstance(MyApplication.mInstance)
        val componentName = ComponentName(MyApplication.mInstance, MyAppWidgetProvider::class.java)
        manager.updateAppWidget(componentName, remoteViews)

        getUserInfo()
        getUserInfo1()

        page = 1
        UserBean.todayBean = 0
        UserBean.ago1Bean = 0
        todayTime = TimeUtil.getTodayMillis(0)
        yesterdayTime = TimeUtil.getTodayMillis(-1)

        getJingBeanData()

        getRedPackge()
    }

    private fun getRedPackge() {
        HttpUtil.getRedPack("https://m.jingxi.com/user/info/QueryUserRedEnvelopesV2?type=1&orgFlag=JD_PinGou_New&page=1&cashRedType=1&redBalanceFlag=1&channel=1&_=" + System.currentTimeMillis() + "&sceneval=2&g_login_type=1&g_ty=ls", object : StringCallBack {
            override fun onSuccess(result: String) {
                try {
                    val redPacket = gson.fromJson(result, RedPacket::class.java)
                    UserBean.hb = redPacket.data.balance
                    UserBean.gqhb = redPacket.data.expiredBalance
                    UserBean.countdownTime = redPacket.data.countdownTime / 60 / 60
                    setData()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            override fun onFail() {
            }
        })
    }

    private fun getUserInfo1() {
        HttpUtil.getUserInfo1(object : StringCallBack {
            override fun onSuccess(result: String) {
                try {
                    val job = JSONObject(result)
                    UserBean.jxiang = job.optJSONObject("user").optString("uclass").replace("京享值", "")
                    setData()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            override fun onFail() {
            }

        })
    }

    private fun getUserInfo() {
        HttpUtil.getUserInfo(object : StringCallBack {
            override fun onSuccess(result: String) {
                try {
                    val job = JSONObject(result)
                    try {
                        UserBean.nickName = job.optJSONObject("data").optJSONObject("userInfo").optJSONObject("baseInfo").optString("nickname")
                        UserBean.userLevel = job.optJSONObject("data").optJSONObject("userInfo").optJSONObject("baseInfo").optString("userLevel")
                        UserBean.levelName = job.optJSONObject("data").optJSONObject("userInfo").optJSONObject("baseInfo").optString("levelName")
                        UserBean.headImageUrl = job.optJSONObject("data").optJSONObject("userInfo").optJSONObject("baseInfo").optString("headImageUrl")
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    try {
                        UserBean.beanNum = job.optJSONObject("data").optJSONObject("assetInfo").optString("beanNum")
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    setData()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            override fun onFail() {
            }

        })
    }

    fun getJingBeanData() {
        HttpUtil.getJD("https://api.m.jd.com/client.action?functionId=getJingBeanBalanceDetail", page, object : StringCallBack {
            override fun onSuccess(result: String) {
                try {
                    //2021-10-05 10:18:37
                    val jingDouBean = gson.fromJson(result, JingDouBean::class.java)
                    val dataList = jingDouBean.detailList
                    var isFinish = true
                    for (i in dataList.indices) {
                        val detail = dataList[i]
                        val beanDay = parseTime(detail.date)!!
                        if (beanDay > todayTime) {
                            if (detail.amount > 0) {
                                UserBean.todayBean = UserBean.todayBean + detail.amount
                            }
                        } else {
                            isFinish = false
                            break
                        }
                    }
                    if (isFinish) {
                        page++
                        getJingBeanData()
                    } else {
                        get1AgoBeanData()
                        setData()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            override fun onFail() {
            }
        })
    }

    fun get1AgoBeanData() {
        HttpUtil.getJD("https://api.m.jd.com/client.action?functionId=getJingBeanBalanceDetail", page, object : StringCallBack {
            override fun onSuccess(result: String) {
                try {
                    val jingDouBean = gson.fromJson(result, JingDouBean::class.java)
                    val dataList = jingDouBean.detailList
                    var isFinish = true
                    for (i in dataList.indices) {
                        val detail = dataList[i]
                        val beanDay = parseTime(detail.date)!!
                        if (beanDay < todayTime && beanDay > yesterdayTime) {
                            if (detail.amount > 0) {
                                UserBean.ago1Bean = UserBean.ago1Bean + detail.amount
                            }
                        } else if (beanDay < yesterdayTime) {
                            isFinish = false
                            break
                        }
                    }
                    if (isFinish) {
                        page++
                        get1AgoBeanData()
                    } else {
                        setData()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            override fun onFail() {
            }
        })
    }

    private fun setData() {
        if ("1" == getString("hideTips")) {
            remoteViews!!.setViewVisibility(R.id.updateTime, View.GONE)
            remoteViews!!.setViewVisibility(R.id.tips, View.GONE)
        } else {
            remoteViews!!.setViewVisibility(R.id.updateTime, View.VISIBLE)
            remoteViews!!.setViewVisibility(R.id.tips, View.VISIBLE)
        }
        if ("1" == getString("hideNichen")) {
            remoteViews!!.setTextViewText(R.id.nickName, "***")
        } else {
            remoteViews!!.setTextViewText(R.id.nickName, UserBean.nickName)
        }
        remoteViews!!.setTextViewText(R.id.beanNum, UserBean.beanNum)
        remoteViews!!.setTextViewText(R.id.todayBean, "+" + UserBean.todayBean)
        remoteViews!!.setTextViewText(R.id.todayBeanNum, UserBean.todayBean.toString())
        remoteViews!!.setTextViewText(R.id.oneAgoBeanNum, UserBean.ago1Bean.toString())
        remoteViews!!.setTextViewText(R.id.updateTime, "数据更新于:" + getCurrentData())
        remoteViews!!.setTextViewText(R.id.hongbao, UserBean.hb)
        try {
            if (getCurrentHH() + UserBean.countdownTime > 24) {
                remoteViews!!.setTextViewText(R.id.guoquHb, "明日过期:" + UserBean.gqhb)
            } else {
                remoteViews!!.setTextViewText(R.id.guoquHb, "今日过期:" + UserBean.gqhb)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            remoteViews!!.setTextViewText(R.id.guoquHb, "今日过期:" + UserBean.gqhb)
        }
        remoteViews!!.setTextViewText(R.id.jingXiang, UserBean.jxiang)
        val cleatInt3 = Intent()
        cleatInt3.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        cleatInt3.action = "com.scott.sayhi"
        val clearIntent3 = PendingIntent.getBroadcast(MyApplication.mInstance, 0, cleatInt3, PendingIntent.FLAG_UPDATE_CURRENT)
        remoteViews!!.setOnClickPendingIntent(R.id.headImg, clearIntent3)
        if (TextUtils.isEmpty(UserBean.headImageUrl)) {
            Glide.with(MyApplication.mInstance)
                .load(R.mipmap.icon_head_def)
                .into(object : SimpleTarget<Drawable?>() {
                    override fun onResourceReady(resource: Drawable, transition: Transition<in Drawable?>?) {
                        val head = BitmapUtil.drawableToBitmap(resource)
                        remoteViews!!.setImageViewBitmap(R.id.headImg, BitmapUtil.createCircleBitmap(head))
                        val manager = AppWidgetManager.getInstance(MyApplication.mInstance)
                        val componentName = ComponentName(MyApplication.mInstance, MyAppWidgetProvider::class.java)
                        manager.updateAppWidget(componentName, remoteViews)
                    }

                    override fun onLoadFailed(errorDrawable: Drawable?) {
                        val manager = AppWidgetManager.getInstance(MyApplication.mInstance)
                        val componentName = ComponentName(MyApplication.mInstance, MyAppWidgetProvider::class.java)
                        manager.updateAppWidget(componentName, remoteViews)
                    }
                })
        } else {
            Glide.with(MyApplication.mInstance)
                .load(UserBean.headImageUrl)
                .into(object : SimpleTarget<Drawable?>() {
                    override fun onResourceReady(resource: Drawable, transition: Transition<in Drawable?>?) {
                        val head = BitmapUtil.drawableToBitmap(resource)
                        remoteViews!!.setImageViewBitmap(R.id.headImg, BitmapUtil.createCircleBitmap(head))
                        val manager = AppWidgetManager.getInstance(MyApplication.mInstance)
                        val componentName = ComponentName(MyApplication.mInstance, MyAppWidgetProvider::class.java)
                        manager.updateAppWidget(componentName, remoteViews)
                    }

                    override fun onLoadFailed(errorDrawable: Drawable?) {
                        val manager = AppWidgetManager.getInstance(MyApplication.mInstance)
                        val componentName = ComponentName(MyApplication.mInstance, MyAppWidgetProvider::class.java)
                        manager.updateAppWidget(componentName, remoteViews)
                    }
                })
        }
    }
}