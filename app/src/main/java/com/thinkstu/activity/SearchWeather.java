package com.thinkstu.activity;

import android.annotation.*;
import android.content.*;
import android.os.*;
import android.widget.*;

import androidx.appcompat.app.*;
import androidx.swiperefreshlayout.widget.*;

import com.google.gson.*;
import com.thinkstu.bean.*;
import com.thinkstu.helper.*;
import com.thinkstu.*;

import java.util.*;

import okhttp3.*;

public class SearchWeather extends AppCompatActivity {
    private EditText                 edit_city;
    private Button                   search_btn;
    private ListView                 listView;
    private Button                   start_btn;
    private SwipeRefreshLayout       swipeRefreshLayout;  //下拉刷新
    private SharedPreferences        cacheShrPre;
    private SharedPreferences.Editor cacheEditor;
    private SharedPreferences        starShaPre;  // 城市缓存
    private SharedPreferences.Editor starEditor;
    private List<String>             weatherList = new ArrayList<>();
    private Spinner                  mySpinner;

    // key 是高德地图的开发者 key，每个开发者需要去高德地图官网申请一个 key
    String key = "99e8de86755a9337458c499b969292a0";

    // 高德 API 文档：https://lbs.amap.com/api/webservice/guide/api/weatherinfo/
    private OkHttpClient client = new OkHttpClient();   // ok
    private Gson         gson   = new Gson();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.search_weather);
        init(); //初始化

        cacheShrPre = getSharedPreferences("WeatherData", MODE_PRIVATE);
        cacheEditor = cacheShrPre.edit();
        // 启动时清空缓存
        cacheEditor.clear();
        cacheEditor.apply();

        // Star：收藏的城市
        starShaPre = getSharedPreferences("StarCity", Context.MODE_PRIVATE);
        starEditor = starShaPre.edit(); // 初始化 editor

        String starCities = starShaPre.getString("city", "");
        if (!starCities.isEmpty()) {
            // 存在缓存数据，获取并显示
            // TODO 应该显示默认信息

            weatherList = gson.fromJson(starCities, List.class);
        }
        // 为 Spinner 设置适配器
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, R.layout.spinner_item, weatherList);
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mySpinner.setAdapter(adapter);
    }

    // @SuppressLint("HandlerLeak") 禁止在此处显示对Handler内部类的警告，因为这种情况在Android中经常发生，且不会造成实际问题。
    @SuppressLint("HandlerLeak")
    private Handler handler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            // 清空页面数据
            listView.setAdapter(null);
            switch (msg.what) {
                case 0 -> Msg.shorts(SearchWeather.this, "城市不存在！");
                case 1 -> Msg.shorts(SearchWeather.this, "服务器无响应！");
                case 2 -> Msg.shorts(SearchWeather.this, "未知错误~");
                case 3 -> {
                    List<Map<String, String>> list = (List<Map<String, String>>) msg.obj;   // 获取数据
                    final SimpleAdapter simpleAdapter = new SimpleAdapter(SearchWeather.this, list, R.layout.weather_listview_item
                            , new String[]{"date", "day_weather", "day_temp", "day_wind", "day_power", "night_weather", "night_temp", "night_wind", "night_power"}
                            , new int[]{R.id.date, R.id.day_weather, R.id.day_temp, R.id.day_wind, R.id.day_power, R.id.night_weather, R.id.night_temp, R.id.night_wind, R.id.night_power});
                    listView.setAdapter(simpleAdapter); // 为ListView绑定适配器
                    Msg.shorts(SearchWeather.this, "查询成功");
                }
            }
        }
    };

    // 代码先根据城市名获取城市编码，再根据城市编码获取天气信息（高德开发平台请求天气查询API中区域编码adcode是必须项，使用编码服务获取区域编码）
    private void getWeatherForecast(final String city) {
        // 使用 cachePreShr 读取缓存数据，如果存在则直接显示
        String cacheCity = cacheShrPre.getString(city, "");
        if (!cacheCity.isEmpty()) {
            // 存在缓存数据，获取并显示
            List weatherList = gson.fromJson(cacheCity, List.class);
            painting(weatherList);
        } else {
            String        geoURL      = "https://restapi.amap.com/v3/geocode/geo?key=" + key + "&address=" + city;
            String        forecastURL = "https://restapi.amap.com/v3/weather/weatherInfo?key=" + key + "&extensions=all&out=json";
            final Request request     = new Request.Builder().url(geoURL).get().build();
            // 开启子线程
            new Thread(() -> {
                try (Response response = client.newCall(request).execute()) {
                    // 请求成功
                    if (response.isSuccessful()) {
                        String result = response.body().string();
                        // 解析json数据
                        JsonObject object = JsonParser.parseString(result).getAsJsonObject();
                        JsonArray  array  = object.get("geocodes").getAsJsonArray();
                        JsonObject info   = array.get(0).getAsJsonObject();
                        // 查询天气状况
                        String        cityCode         = info.get("adcode").getAsString();
                        String        newUrl           = forecastURL + "&city=" + cityCode;
                        final Request requestForecast  = new Request.Builder().url(newUrl).get().build();
                        Response      responseForecast = client.newCall(requestForecast).execute();
                        //请求成功
                        if (responseForecast.isSuccessful()) {
                            String                    resultForecast = responseForecast.body().string();
                            List<Map<String, String>> weatherList    = new ArrayList<>();  // 存储解析json字符串得到的天气信息
                            Weather                   weather        = gson.fromJson(resultForecast, Weather.class);     // Gson解析
                            // 定义日期数组
                            String[] dates = {"今天", "明天", "后天", "大后天"};
                            // forecastList 是天气预报信息集合，每天的天气信息是一个对象，所以需要遍历集合，获取每天的天气信息
                            List<Casts> forecastList = weather.getForecasts().get(0).getCasts();
                            for (int i = 0; i < forecastList.size() && i < dates.length; i++) {
                                Casts               cast = forecastList.get(i);
                                Map<String, String> map  = new HashMap<>();

                                map.put("date", dates[i]);  // 日期
                                map.put("day_weather", cast.getDayweather());     // 白天天气
                                map.put("day_temp", cast.getDaytemp() + "℃");     // 温度
                                map.put("day_wind", cast.getDaywind() + "风");    // 风向
                                map.put("day_power", cast.getDaypower() + "级");  // 风力
                                map.put("night_weather", cast.getNightweather());    // 夜间天气
                                map.put("night_temp", cast.getNighttemp() + "℃");    // 温度
                                map.put("night_wind", cast.getNightwind() + "风");   // 风向
                                map.put("night_power", cast.getNightpower() + "级"); // 风力
                                weatherList.add(map);
                            }
                            painting(weatherList);
                            // 将 weatherList 写入缓存
                            cacheEditor.putString(city, gson.toJson(weatherList));
                            cacheEditor.apply();
                        }
                    }
                } catch (Exception e) {
                    Message message = Message.obtain();
                    message.what = 0;
                    handler.sendMessage(message);
                    e.printStackTrace();
                }
            }).start();
        }
    }

    private void painting(List<Map<String, String>> weatherList) {
        Message message = Message.obtain();
        message.what = 3;   // 3表示请求成功
        message.obj  = weatherList;
        handler.sendMessage(message);
    }

    /**
     * 利用SharedPreferences保存关注信息
     * 先存入SharedPreferences然后Toast提示
     */
    // TODO 关注功能
    private void saveStar() {
        // 获取输入的城市名
        String city = edit_city.getText().toString();
        weatherList.add(city);
        starEditor.putString("city", gson.toJson(weatherList));
        starEditor.apply();
        Msg.shorts(SearchWeather.this, "关注成功");
    }

    public void refreshDiaries() {
        String address = edit_city.getText().toString();
        getWeatherForecast(address);
        swipeRefreshLayout.setRefreshing(false);    // 刷新操作完成，隐藏刷新进度条
    }

    private void init() {
        edit_city          = findViewById(R.id.edit_city);
        search_btn         = findViewById(R.id.search_w_btn);
        start_btn          = findViewById(R.id.start_btn);
        listView           = findViewById(R.id.search_weather);
        swipeRefreshLayout = findViewById(R.id.swipe_refresh_layout);
        mySpinner          = (Spinner) findViewById(R.id.my_spinner);
        listView.setCacheColorHint(10); // 空间换时间
        swipeRefreshLayout.setOnRefreshListener(() -> refreshDiaries());    // 下拉刷新监听器
        search_btn.setOnClickListener(v -> {
            String address = edit_city.getText().toString();
            getWeatherForecast(address);
        });
        start_btn.setOnClickListener(v -> saveStar());
    }
}