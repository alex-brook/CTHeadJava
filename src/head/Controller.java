package head;

import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Slider;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.stage.Stage;

import java.util.Objects;

public class Controller {

    @FXML
    private Slider sliceSlide;
    @FXML
    private Slider zoomSlide;
    @FXML
    private ComboBox<String> planeCombo;
    @FXML
    private ImageView medicalImage;
    @FXML
    private ImageView thumbImage;

    private Head head;
    private Stage stage;

    @FXML
    public void initialize(){
        head = new Head("CThead", (float) zoomSlide.getValue());
        planeCombo.getItems().addAll(Head.View.TOP.getLabel(),
                Head.View.FRONT.getLabel(), Head.View.SIDE.getLabel());
        planeCombo.setValue(planeCombo.getItems().get(0));
        zoomSlide.setMax(Head.MAX_SF);
        updateImage();
        updateThumbnailImage();
    }

    @FXML
    public void mipButtonClicked() {
        head.toggleMip();
        updateImage();
    }

    @FXML
    public void changeSlice() {
        head.slice((int) sliceSlide.getValue());
        updateImage();
        updateThumbnailImage();
    }

    @FXML
    public void changeZoom() {
        head.setZoom((float) zoomSlide.getValue());
        updateImage();
        resizeWindow();
    }

    @FXML
    public void changeView() {
        head.setView(Objects.requireNonNull(
                Head.View.valueOfLabel(planeCombo.getValue())));
        sliceSlide.setValue(Math.min(sliceSlide.getValue(), head.getMaxSlice()));
        sliceSlide.setMax(head.getMaxSlice());
        updateImage();
        updateThumbnailImage();
        resizeWindow();
    }

    @FXML
    private void toggleEq() {
        head.toggleEqualisation();
        head.refresh();
    }

    @FXML
    private void toggleBilinear() {
        head.toggleBilinear();
        head.refresh();
    }

    public void setStage(Stage s) {
        stage = s;
    }

    private void updateImage() {
        head.updateImageDimensions();
        head.refresh();
        medicalImage.setImage(head.getImage());
        medicalImage.setFitWidth(medicalImage.getImage().getWidth());
        medicalImage.setFitHeight(medicalImage.getImage().getHeight());
    }

    private void updateThumbnailImage() {
        thumbImage.setImage(head.getThumbnailImage());
        thumbImage.setFitHeight(thumbImage.getImage().getHeight());
        thumbImage.setFitWidth(thumbImage.getImage().getWidth());
    }

    private void resizeWindow() {
        if(stage != null) {
            stage.sizeToScene();
        }
    }
}
