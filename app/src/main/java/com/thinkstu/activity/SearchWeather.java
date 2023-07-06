package com.thinkstu.activity;

import android.annotation.*;
import android.content.*;
import android.os.*;
import android.view.*;
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
    private ListView                 listView;
    private Button                   search_btn;
    private Button                   star_btn;
    private TextView                 reportTimeTextView;
    private SwipeRefreshLayout       swipeRefreshLayout;  //下拉刷新
    private SharedPreferences        cacheShrPre;   // 数据缓存
    private SharedPreferences.Editor cacheEditor;
    private SharedPreferences        starShaPre;    // 城市缓存
    private SharedPreferences.Editor starEditor;
    private List<String>             weatherList = new ArrayList<>();   // 天气数据集合
    private Spinner                  citySpinner;

    // key 是高德地图的开发者标识，开发者需要申请 key。API 文档：https://lbs.amap.com/api/webservice/guide/api/weatherinfo/
    String key = "99e8de86755a9337458c499b969292a0";

    private OkHttpClient okHttpClient = new OkHttpClient();   // ok
    private Gson         gson         = new Gson();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.search_weather);
        init(); //初始化
        // 缓存本次查询的城市
        cacheShrPre = getSharedPreferences("WeatherData", MODE_PRIVATE);
        cacheEditor = cacheShrPre.edit();
        // 启动时清空缓存
        cacheEditor.clear();
        cacheEditor.apply();

        // Star：收藏的城市
        starShaPre = getSharedPreferences("StarCity", Context.MODE_PRIVATE);
        starEditor = starShaPre.edit();
        String starCities = starShaPre.getString("city", "");
        // 如果存在缓存数据的话，获取并显示（这里利用 HashSet 去重）
        if (!starCities.isEmpty()) {
            weatherList.add(0, "已收藏的城市列表");
            weatherList.addAll(gson.fromJson(starCities, List.class));
            weatherList = new ArrayList<>(new HashSet<>(weatherList));
        }

        // 为 Spinner 设置适配器
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, R.layout.spinner_item, weatherList);
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        citySpinner.setAdapter(adapter);
        citySpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selectedItem = parent.getItemAtPosition(position).toString();
                if (!selectedItem.equals("已收藏的城市列表")) {
                    Msg.shorts(SearchWeather.this, "你选择了: " + selectedItem);

                    String address = edit_city.getText().toString();
                    getWeatherForecast(address);

                    edit_city.setText(selectedItem);    // 输入框显示选择的城市
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
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

    // 代码先根据城市名获取城市编码，再根据城市编码获取天气信息（高德开发平台请求天气查询API中区域编码 adcode 是必须项，使用编码服务获取区域编码）
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
                try (Response response = okHttpClient.newCall(request).execute()) {
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
                        Response      responseForecast = okHttpClient.newCall(requestForecast).execute();
                        //请求成功
                        if (responseForecast.isSuccessful()) {
                            String                    resultForecast = responseForecast.body().string();
                            List<Map<String, String>> weatherList    = new ArrayList<>();  // 存储解析json字符串得到的天气信息
                            Weather                   weather        = gson.fromJson(resultForecast, Weather.class);     // Gson解析
                            // 定义日期数组
                            String[] dates = {"今天", "明天", "后天"};
                            // 切换至主线程设置时间（UI）
                            runOnUiThread(() -> reportTimeTextView.setText("数据最后更新时间：" + weather.getForecasts().get(0).getReporttime()));

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

    // 保存关注的城市
    private void saveStar() {
        // 获取输入的城市名
        String city = edit_city.getText().toString();
        // 正则表达式验证城市名是否合法：2-10个汉字
        if (!city.matches("^[\\u4e00-\\u9fa5]{2,10}$")) {
            Msg.shorts(SearchWeather.this, "城市不存在！");
            return;
        } else if (city.isEmpty()) {
            Msg.shorts(SearchWeather.this, "请输入城市名");
            return;
        }
        // 获取关注列表
        if (!star_btn.getText().toString().equals("已收藏的城市列表")) {
            // 获取关注列表
            // 遍历列表，如果存在则移除
            for (int i = 0; i < weatherList.size(); i++) {
                if (weatherList.get(i).equals(city)) {
                    weatherList.remove(i);
                    // 将新的关注列表写入缓存
                    starEditor.putString("city", gson.toJson(new ArrayList<>(new HashSet<>(weatherList))));
                    starEditor.apply();
                    Msg.shorts(SearchWeather.this, "取关成功");
                    return;
                }
            }
        }
        weatherList.add(city);
        starEditor.putString("city", gson.toJson(new ArrayList<>(new HashSet<>(weatherList))));
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
        star_btn           = findViewById(R.id.start_btn);
        reportTimeTextView = findViewById(R.id.report_time);
        listView           = findViewById(R.id.search_weather);
        swipeRefreshLayout = findViewById(R.id.swipe_refresh_layout);
        citySpinner        = findViewById(R.id.my_spinner);
        listView.setCacheColorHint(4); // 空间换时间
        swipeRefreshLayout.setOnRefreshListener(() -> refreshDiaries());         // 下拉刷新监听器
        search_btn.setOnClickListener(v -> {
            String address = edit_city.getText().toString();
            getWeatherForecast(address);
        });
        star_btn.setOnClickListener(v -> saveStar());
    }
}
