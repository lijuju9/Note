package com.thinkstu.activity;

import android.annotation.*;
import android.content.*;
import android.os.*;
import android.util.*;
import android.widget.*;

import androidx.appcompat.app.*;
import androidx.swiperefreshlayout.widget.*;

import com.google.gson.*;
import com.thinkstu.bean.*;
import com.thinkstu.helper.*;
import com.thinkstu.util.*;
import com.thinkstu.*;

import java.util.*;

import okhttp3.*;

public class SearchWeather extends AppCompatActivity {
    private EditText edit_city;
    private Button   search_btn;
    private ListView listView;
    private Button   start_btn;
    private TextView city_text;
    private TextView weather_text;
    private TextView tem_text;
    SwipeRefreshLayout swipeRefreshLayout;  //下拉刷新

    // key 是高德地图的开发者 key，每个开发者需要去高德地图官网申请一个 key
    String key = "99e8de86755a9337458c499b969292a0";

    // 高德 API 文档：https://lbs.amap.com/api/webservice/guide/api/weatherinfo/
    private String       url    = "https://restapi.amap.com/v3/weather/weatherInfo?key=" + key + "&extensions=all&out=json";
    private OkHttpClient client = new OkHttpClient();   // ok
    private Gson         gson   = new Gson();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.search_weather);
        //初始化
        init();
        SharedPreferences shaPre   = getSharedPreferences("cityWeather", Context.MODE_PRIVATE);
        String            saveCity = shaPre.getString("saveCity", "");
        String            tem      = shaPre.getString("tem", "");
        String            weather  = shaPre.getString("weather", "");
        // 如果有保存的城市则显示
        if (!saveCity.isEmpty()) {
            city_text.setText(saveCity);
            weather_text.setText(weather);
            tem_text.setText(tem);
        }
    }

    // @SuppressLint("HandlerLeak") 禁止在此处显示对Handler内部类的警告，因为这种情况在Android中经常发生，且不会造成实际问题。
    @SuppressLint("HandlerLeak")
    private Handler handler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 0 -> {
                    // 0: 城市输入有误，清空 ListView
                    Msg.shorts(SearchWeather.this, "城市输入有误！");
                    listView.setAdapter(null);
                    clean();
                }
                case 1 -> {
                    // 1: 服务器无响应，清空 ListView
                    Msg.shorts(SearchWeather.this, "服务器无响应！");
                    clean();
                }
                case 2 -> {
                    // 2: 其他情况（例如无网络连接），清空 ListView
                    clean();
                    Msg.shorts(SearchWeather.this, "未知错误~");
                }
                case 3 -> {
                    // 3: 查询成功，显示天气信息
                    List<Map<String, String>> list = (List<Map<String, String>>) msg.obj;
                    //创建Adapter
                    final SimpleAdapter simpleAdapter = new SimpleAdapter(SearchWeather.this
                            , list, R.layout.weather_listview_item
                            , new String[]{"date", "day_weather", "day_temp", "day_wind", "day_power"
                            , "night_weather", "night_temp", "night_wind", "night_power"}
                            , new int[]{R.id.date, R.id.day_weather, R.id.day_temp, R.id.day_wind, R.id.day_power
                            , R.id.night_weather, R.id.night_temp, R.id.night_wind, R.id.night_power});
                    //绑定Adapter
                    listView.setAdapter(simpleAdapter);
                    clean();
                    Msg.shorts(SearchWeather.this, "查询成功");
                }
                default -> {
                }
            }

        }
    };

    private void clean() {
        tem_text.setText("");
        weather_text.setText("");
    }

    @SuppressLint("HandlerLeak")
    private Handler handler_star = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 0 -> {
                    Msg.shorts(SearchWeather.this, "城市不存在！");
                    listView.setAdapter(null);
                }
                case 1 -> Msg.shorts(SearchWeather.this, "服务器无响应！");
                case 2 -> {
                    clean();
                    Msg.shorts(SearchWeather.this, "未知错误~");
                }
                case 3 -> {
                    List<Map<String, String>> list = (List<Map<String, String>>) msg.obj;
                    //创建Adapter
                    final SimpleAdapter simpleAdapter = new SimpleAdapter(SearchWeather.this
                            , list, R.layout.weather_listview_item
                            , new String[]{"date", "day_weather", "day_temp", "day_wind", "day_power"
                            , "night_weather", "night_temp", "night_wind", "night_power"}
                            , new int[]{R.id.date, R.id.day_weather, R.id.day_temp, R.id.day_wind, R.id.day_power
                            , R.id.night_weather, R.id.night_temp, R.id.night_wind, R.id.night_power});
                    //绑定Adapter
                    listView.setAdapter(simpleAdapter);
                    //更新界面上展示的天气信息
                    SharedPreferences        shaPre = getSharedPreferences("cityWeather", Context.MODE_PRIVATE);
                    SharedPreferences.Editor editor = shaPre.edit();

                    String nowCity = shaPre.getString("nowCity", "");
                    String tem     = shaPre.getString("tem", "");
                    String weather = shaPre.getString("weather", "");
                    editor.putString("saveCity", nowCity);
                    editor.commit();

                    // 保存关注信息
                    String saveCity = shaPre.getString("saveCity", "");
                    city_text.setText(saveCity);
                    tem_text.setText(tem);
                    weather_text.setText(weather);
                }
                default -> {
                }
            }
        }
    };

    /**
     * 利用SharedPreferences保存关注信息
     * 先存入SharedPreferences然后Toast提示
     */
    private void saveStar() {
        //获取输入
        String saveCityIn = edit_city.getText().toString();
        //获取sharedPreferences
        SharedPreferences        sharedPreferences = getSharedPreferences("cityWeather", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor            = sharedPreferences.edit();

        String nowCity  = sharedPreferences.getString("nowCity", "");
        String saveCity = sharedPreferences.getString("saveCity", "");
        String tem      = sharedPreferences.getString("tem", "");
        String weather  = sharedPreferences.getString("weather", "");
        //输入框为空 取消关注
        if (saveCityIn.equals("")) {
            editor.putString("nowCity", "");
            editor.putString("saveCity", "");
            editor.putString("tem", "");
            editor.putString("weather", "");
            editor.commit();
            Msg.shorts(SearchWeather.this, "取消关注成功！");
            //清空界面
            listView.setAdapter(null);
            city_text.setText("");
            tem_text.setText("");
            weather_text.setText("");
        }
        //输入城市名和已关注的城市名相同 则更新关注
        else if (saveCityIn.equals(saveCity)) {
            editor.putString("nowCity", saveCityIn);
            getCityCodeStar(saveCityIn);
            Msg.shorts(SearchWeather.this, "更新成功！");
        }
        //默认添加关注
        else {
            city_text.setText("");
            tem_text.setText("");
            weather_text.setText("");
            editor.putString("nowCity", saveCityIn);
            editor.commit();
            getCityCodeStar(saveCityIn);
            //Toast提醒
            Msg.shorts(SearchWeather.this, "关注成功！");
        }
    }
    // 根据 城市名 获取 城市编码并请求天气查询

    /**
     * 高德开发平台请求天气查询API中区域编码adcode是必须项，使用高德地理编码服务获取区域编码
     * address:结构化地址信息，规则遵循：国家、省份、城市、区县、城镇、乡村、街道、门牌号码、屋邨、大厦
     */
    private void getAddressCode(final String city) {
        String        url     = "https://restapi.amap.com/v3/geocode/geo?key=" + key + "&address=" + city;
        final Request request = new Request.Builder().url(url).get().build();
        // 开启子线程
        new Thread(() -> {
            try (Response response = client.newCall(request).execute();) {
                // 请求成功
                if (response.isSuccessful()) {
                    String result = response.body().string();
                    // 解析json数据
                    JsonObject object = JsonParser.parseString(result).getAsJsonObject();
                    JsonArray  array  = object.get("geocodes").getAsJsonArray();
                    JsonObject info   = array.get(0).getAsJsonObject();
                    // 获取cityCode
                    String cityCode = info.get("adcode").getAsString();
                    //请求天气查询
                    getWeather(cityCode);
                }
            } catch (Exception e) {
                Message message = Message.obtain();
                message.what = 0;
                handler.sendMessage(message);
                e.printStackTrace();
            }
        }).start();
    }

    /**
     * 查询天气
     */
    private void getWeather(String cityCode) {
        String        newUrl  = url + "&city=" + cityCode;
        final Request request = new Request.Builder().url(newUrl).get().build();
        new Thread(() -> {
            try (Response response = client.newCall(request).execute();) {
                //请求成功
                if (response.isSuccessful()) {
                    String                    result      = response.body().string();
                    List<Map<String, String>> weatherList = new ArrayList<>();  // 存储解析json字符串得到的天气信息
                    Weather                   weather     = gson.fromJson(result, Weather.class);     // Gson解析
                    // 定义日期
                    String[] dates = {"今天", "明天", "后天", "大后天"};
                    // 获取 Casts 列表并遍历
                    List<Casts> castsList = weather.getForecasts().get(0).getCasts();
                    for (int i = 0; i < castsList.size() && i < dates.length; i++) {
                        Casts               cast = castsList.get(i);
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
                    //将服务器返回数据写入 Handler
                    Message message = Message.obtain();
                    message.what = 3;   // 3表示请求成功
                    message.obj  = weatherList;
                    handler.sendMessage(message);
                }
            } catch (Exception e) {
                Message message = Message.obtain();
                message.what = 1;
                handler.sendMessage(message);
                e.printStackTrace();
            }
        }).start();
    }

    //关注按钮的实现
    private void getCityCodeStar(final String city) {
        String url = "https://restapi.amap.com/v3/geocode/geo?key=6d29a580912e6f4b7ffb3b057d1f9ab2&address=" + city;
        final Request request = new Request.Builder().url(url).get().build();
        new Thread(() -> {
            try (Response response = client.newCall(request).execute();) {
                //请求成功
                if (response.isSuccessful()) {
                    String result = response.body().string();
                    //转 JsonObject、JsonArray 处理
                    JsonObject object = JsonParser.parseString(result).getAsJsonObject();
                    JsonArray  array  = object.get("geocodes").getAsJsonArray();
                    JsonObject info   = array.get(0).getAsJsonObject();
                    //获取adcode
                    String cityCode = info.get("adcode").getAsString();
                    //请求天气查询
                    getWeatherStar(cityCode);
                }
            } catch (Exception e) {
                Message message = Message.obtain();
                message.what = 1;
                handler_star.sendMessage(message);
                e.printStackTrace();
            }
        }).start();
    }

    /**
     * 查询天气
     */
    private void getWeatherStar(String adcode) {
        String        newUrl  = url + "&&city=" + adcode;
        final Request request = new Request.Builder().url(newUrl).get().build();
        new Thread(() -> {
            Response response;
            try {
                response = client.newCall(request).execute();
                //请求成功
                if (response.isSuccessful()) {
                    String result = response.body().string();

                    Log.i("服务器返回的结果:", result);
                    //存储解析json字符串得到的天气信息
                    List<Map<String, String>> weatherList = new ArrayList<>();

                    //使用Gson解析
                    Weather weather = gson.fromJson(result, Weather.class);
                    //获取今天天气信息
                    Casts today = weather.getForecasts().get(0).getCasts().get(0);
                    //如果SharedPreferences不为空，则已关注的更新天气
                    SharedPreferences sharedPreferences = getSharedPreferences("cityWeather", Context.MODE_PRIVATE);
                    String            nowcity           = sharedPreferences.getString("nowcity", "");
                    if (!nowcity.equals("")) {
                        SharedPreferences.Editor editor = sharedPreferences.edit();
                        editor.putString("weather", today.getDayweather());
                        editor.putString("tem", today.getDaytemp() + "℃");
                        editor.commit();
                    }
                    // 定义日期
                    String[] dates = {"今天", "明天", "后天", "大后天"};
                    // 获取Casts列表
                    List<Casts> castsList = weather.getForecasts().get(0).getCasts();
                    // 遍历Casts列表
                    for (int i = 0; i < castsList.size() && i < dates.length; i++) {
                        addWeatherInfoToMap(castsList.get(i), dates[i], weatherList);
                    }
                    //将服务器返回数据写入Handler
                    Message message = Message.obtain();
                    message.what = 3;
                    message.obj  = weatherList;
                    handler_star.sendMessage(message);
                }
            } catch (Exception e) {
                Log.i("SearchWeather.java", "服务器异常:" + e);
                Message message = Message.obtain();
                message.what = 1;
                message.obj  = e.toString();
                handler_star.sendMessage(message);
                e.printStackTrace();
            }
        }).start();
    }

    // 方法：将Casts对象的天气信息添加到Map，然后将Map添加到weatherList
    private void addWeatherInfoToMap(Casts cast, String date, List<Map<String, String>> weatherList) {
        Map<String, String> map = new HashMap<>();
        map.put("date", date);
        map.put("day_weather", cast.getDayweather());
        map.put("day_temp", cast.getDaytemp() + "℃");
        if (WeatherUtil.noWindDirection(cast.getDaywind())) {
            map.put("day_wind", cast.getDaywind());
        } else {
            map.put("day_wind", cast.getDaywind() + "风");
        }
        map.put("day_power", cast.getDaypower() + "级");
        map.put("night_weather", cast.getNightweather());
        map.put("night_temp", cast.getNighttemp() + "℃");
        if (WeatherUtil.noWindDirection(cast.getNightwind())) {
            map.put("night_wind", cast.getNightwind());
        } else {
            map.put("night_wind", cast.getNightwind() + "风");
        }
        map.put("night_power", cast.getNightpower() + "级");
        weatherList.add(map);
    }

    public void refreshDiaries() {
        String address = edit_city.getText().toString();
        getAddressCode(address);
        // 刷新操作完成，隐藏刷新进度条
        swipeRefreshLayout.setRefreshing(false);
    }

    private void init() {
        edit_city          = findViewById(R.id.edit_city);
        search_btn         = findViewById(R.id.search_w_btn);
        start_btn          = findViewById(R.id.start_btn);
        city_text          = findViewById(R.id.city);
        weather_text       = findViewById(R.id.weather);
        tem_text           = findViewById(R.id.tem);
        listView           = findViewById(R.id.search_weather);
        swipeRefreshLayout = findViewById(R.id.swipe_refresh_layout);
        listView.setCacheColorHint(10); // 空间换时间
        swipeRefreshLayout.setOnRefreshListener(() -> refreshDiaries());    // 下拉刷新监听器
        search_btn.setOnClickListener(v -> {
            String address = edit_city.getText().toString();
            getAddressCode(address);
            city_text = (TextView) findViewById(R.id.city);
            city_text.setText(address);
            tem_text.setText("");
            weather_text.setText("");
        });
        start_btn.setOnClickListener(v -> saveStar());
    }
}