package head;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import javax.naming.ldap.Control;

public class Main extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception{
        FXMLLoader fxl = new FXMLLoader();
        Parent root = fxl.load(getClass().getResource("head.fxml").openStream());
        Controller cont = fxl.getController();
        cont.setStage(primaryStage);
        primaryStage.setTitle("CS-255 CThead");
        primaryStage.setScene(new Scene(root));
        primaryStage.show();
    }


    public static void main(String[] args) {
        launch(args);
    }
}
