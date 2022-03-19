package com.civitasv.spider.controller;

import com.civitasv.spider.MainApplication;
import com.civitasv.spider.db.Database;
import com.civitasv.spider.helper.CoordinateType;
import com.civitasv.spider.util.MessageUtil;
import com.civitasv.spider.viewmodel.POIViewModel;
import com.sun.javafx.collections.ObservableListWrapper;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.apache.commons.lang3.StringUtils;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.sql.SQLException;
import java.util.List;
import java.util.*;
import java.util.regex.Pattern;

public class POIController {
    private static Scene scene;

    public TextField threadNum; // 线程数目
    public TextField keywords; // 关键字
    public TextArea keys; // 高德 API Key
    public TextField types; // 类型
    public TextField adCode; // 行政区六位代码
    public TextField rectangle; // 矩形左上角#矩形右下角
    public TextField threshold; // 阈值
    public ChoiceBox<String> format; // 输出格式
    public TextField outputDirectory; // 输出文件夹
    public TextArea messageDetail; // 输出信息
    public TextField userFile; // 用户自定义文件
    public TextField failJobsFile; // 失败任务文件
    public TabPane tabs; // tab 栏
    public Button directoryBtn; // 点击选择文件夹
    public Button execute; // 执行
    public Button poiType; // 点击查看 poi 类型
    public ChoiceBox<String> userType; // 用户类型
    public ChoiceBox<CoordinateType> rectangleCoordinateType; // 矩形坐标格式
    public ChoiceBox<CoordinateType> userFileCoordinateType; // 用户自定义文件坐标格式
    public MenuItem wechat; // 微信
    public MenuItem joinQQ; // QQ群

    // added by leon
    public ChoiceBox<String> poiCate1; // POI大类
    public ChoiceBox<String> poiCate2; // POI中类
    public ChoiceBox<String> poiCate3; // POI小类

    // 数据库操作对象
    private Database database;

    public Database getDatabase() {
        return database;
    }

    // 大中小类
    private String cate1, cate2, cate3;
    private String curCategoryId;
    Set<String> choosedPoiCategory;

    // 主界面
    private Stage mainStage;

    public Stage getMainStage() {
        return mainStage;
    }

    private POIViewModel poiViewModel;

    public void show() throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(MainApplication.class.getResource("poi.fxml"));
        Parent root = fxmlLoader.load();
        POIController controller = fxmlLoader.getController();
        controller.init();
        Stage stage = new Stage();
        stage.setResizable(false);
        stage.setTitle("POIKit");
        scene = new Scene(root);
        scene.getStylesheets().add(Objects.requireNonNull(MainApplication.class.getResource("styles.css")).toString());
        stage.setScene(scene);
        stage.getIcons().add(new Image(Objects.requireNonNull(MainApplication.class.getResourceAsStream("icon/icon.png"))));
        this.mainStage = stage;
        stage.show();
    }

    private void init() {
        this.poiViewModel = new POIViewModel(threadNum, keywords, keys, types, adCode,
                rectangle, threshold, format, outputDirectory, messageDetail, userFile,failJobsFile, tabs, directoryBtn,
                execute, poiType, userType, rectangleCoordinateType, userFileCoordinateType, wechat, joinQQ);
        this.threadNum.setTextFormatter(getFormatter());
        this.threshold.setTextFormatter(getFormatter());
        this.adCode.setTextFormatter(getFormatter());
        List<CoordinateType> list = Arrays.asList(CoordinateType.WGS84, CoordinateType.BD09, CoordinateType.GCJ02);
        this.rectangleCoordinateType.setItems(new ObservableListWrapper<>(list));
        this.userFileCoordinateType.setItems(new ObservableListWrapper<>(list));
        this.rectangleCoordinateType.getSelectionModel().selectFirst();
        this.userFileCoordinateType.getSelectionModel().selectFirst();
        wechat.setOnAction(event -> {
            try {
                openAbout(false);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        joinQQ.setOnAction(event -> {
            try {
                openAbout(true);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });

        /*
         * 改为从数据库选择POI类型
         * added by leon
         */
        this.database = new Database();
        this.choosedPoiCategory = new HashSet<>();

        // 设置key
        this.keys.setText("");

        // 设置cate1下拉
        refreshChoiceBoxCate1();

        this.poiCate1.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> refreshChoiceBoxCate2(newValue));

        this.poiCate2.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> refreshChoiceBoxCate3(newValue));

        this.poiCate3.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> getPoiCategory(newValue));
    }

    private void refreshChoiceBoxCate1() {
        List<String> arrCate1;
        try {
            // 获取POI大类
            arrCate1 = database.getPoiCategory1();
            this.poiCate1.setItems(new ObservableListWrapper<>(arrCate1));
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void refreshChoiceBoxCate2(String cate1) {
        this.cate1 = cate1;
        List<String> arrCate2;
        try {
            arrCate2 = database.getPoiCategory2(this.cate1);
            this.poiCate2.setItems(new ObservableListWrapper<>(arrCate2));
            this.poiCate2.setValue("");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void refreshChoiceBoxCate3(String cate2) {
        this.cate2 = cate2;
        List<String> arrCate3;
        try {
            arrCate3 = database.getPoiCategory3(this.cate1, this.cate2);
            this.poiCate3.setItems(new ObservableListWrapper<>(arrCate3));
            this.poiCate3.setValue("");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void getPoiCategory(String cate3) {
        this.cate3 = cate3;
        try {
            this.curCategoryId = database.getPoiCategoryId(this.cate1, this.cate2, this.cate3);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void addPoiCategory() {
        if ((this.poiCate1.getValue().equals("")) || (this.poiCate2.getValue().equals("")) || (this.poiCate3.getValue().equals(""))) {
            MessageUtil.alert(Alert.AlertType.ERROR, "类型错误", null, "请完整选择POI类型！");
            return;
        }
        this.choosedPoiCategory.add(this.curCategoryId);
        String splitSetWithComma = StringUtils.join(choosedPoiCategory.toArray(), ",");
        this.types.setText(splitSetWithComma);
    }

    private TextFormatter<Integer> getFormatter() {
        return new TextFormatter<>(
                c -> Pattern.matches("\\d*", c.getText()) ? c : null);
    }

    public void execute() {
        poiViewModel.execute();
    }

    public void cancel() {
        poiViewModel.cancel();
    }

    public void openCityChoose() throws IOException {
        CityChooseController controller = new CityChooseController();
        controller.show(this);
    }

    public void chooseAdCode() throws IOException {
        openCityChoose();
    }

    public void chooseFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("选择输入文件");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("geojson", "*.json")
        );
        File file = fileChooser.showOpenDialog(scene.getWindow());
        if (file != null)
            userFile.setText(file.getAbsolutePath());
    }

    public void chooseFailJobsFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("选择输入文件");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("csv", "*.csv")
        );
        File file = fileChooser.showOpenDialog(scene.getWindow());
        if (file != null)
            failJobsFile.setText(file.getAbsolutePath());
    }

    public void chooseDirectory() {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("选择输出文件夹");
        File file = directoryChooser.showDialog(scene.getWindow());
        if (file != null)
            outputDirectory.setText(file.getAbsolutePath());
    }

    public void openPOITypes() {
        choosedPoiCategory.clear();
        this.types.setText("");
    }

    public void openGeocoding() throws IOException {
        GeocodingController controller = new GeocodingController();
        controller.show();
    }

    public void openSpatialTransform() throws IOException {
        SpatialDataTransformController controller = new SpatialDataTransformController();
        controller.show();
    }

    public void openCoordinateTransform() throws IOException {
        CoordinateTransformController controller = new CoordinateTransformController();
        controller.show();
    }

    public void openAbout(boolean isQQ) throws IOException {
        AboutController controller = new AboutController();
        controller.show(isQQ);
    }

    public void openDonate() throws IOException {
        DonateController controller = new DonateController();
        controller.show();
    }

    public void starsMe() throws URISyntaxException, IOException {
        Desktop.getDesktop().browse(new URI("https://github.com/Civitasv/AMapPoi"));
    }
}
