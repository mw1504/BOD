package sepr.game;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.viewport.ScreenViewport;

/**
 * base class for handling phase specific input
 */
public abstract class Phase extends Stage {

    protected GameScreen gameScreen;
    protected Map map;
    protected Player currentPlayer;

    private Table table;
    private Label bottomBarRightPart;
    private TurnPhaseType turnPhase;

    private Label playerNameLabel;
    private Label additionalInformationLabel; // label for writing extra phase specific information, i.e. troops to allocate in the turn phase
    private Label turnTimerLabel;
    private Image collegeLogo;

    private static Texture gameHUDBottomBarLeftPartTexture;

    public Phase(GameScreen gameScreen, Map map, TurnPhaseType turnPhase) {
        this.setViewport(new ScreenViewport());

        this.gameScreen = gameScreen;
        this.map = map;

        this.turnPhase = turnPhase;

        this.table = new Table();
        this.table.setFillParent(true); // make ui table fill the entire screen
        this.addActor(table);
        this.table.setDebug(false); // enable table drawing for ui debug

        gameHUDBottomBarLeftPartTexture = new Texture("uiComponents/HUD-Bottom-Bar-Left-Part.png");

        this.setupUi();
    }


    private void setupUi() {
        TextButton endPhaseButton = WidgetFactory.genEndPhaseButton("END PHASE");
        endPhaseButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                gameScreen.advancePhase();
            }
        });
        bottomBarRightPart = WidgetFactory.genGameHUDBottomBarRightPart("INIT");
        Table bottomBarLeftPart = genGameHUDBottomBarLeftPart(GameSetupScreen.CollegeName.UNI_OF_YORK, "INIT", "INIT", "Turn Timer: DISABLED");

        table.top().center();
        table.add(WidgetFactory.genGameHUDTopBar(turnPhase, new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                gameScreen.openMenu();
            }
        })).colspan(2).expandX().height(60).width(910);

        table.row();
        table.add(new Table()).expand();

        Table subTable = new Table();

        subTable.bottom();
        subTable.add(bottomBarLeftPart).height(190).width(250);
        subTable.add(bottomBarRightPart).bottom().expandX().fillX().height(60);

        table.row();
        table.add(subTable).expandX().fill();
        table.bottom().right();
        table.add(endPhaseButton).fill().height(60).width(170);

        setBottomBarText(null);
    }

    /**
     * Generates the UI widget to be displayed at the bottom left of the HUD
     * @param collegeName  name of college chosen by player
     * @param playerName name of the player
     * @param additionalInformation text for writing extra phase specific information, i.e. troops to allocate in the turn phase
     * @param turnTimer time for the turn
     * @return table containing the information to display in the HUD
     */
    private Table genGameHUDBottomBarLeftPart(GameSetupScreen.CollegeName collegeName, String playerName, String additionalInformation, String turnTimer){
        Label.LabelStyle style = new Label.LabelStyle();
        style.font = WidgetFactory.getFontSmall();
        playerNameLabel = new Label(playerName, style);
        additionalInformationLabel = new Label(additionalInformation, style);
        turnTimerLabel = new Label(turnTimer, style);
        collegeLogo = new Image(WidgetFactory.genCollegeLogoDrawable(collegeName));

        Table table = new Table();
        table.background(new TextureRegionDrawable(new TextureRegion(gameHUDBottomBarLeftPartTexture)));

        Table subTable = new Table();
        subTable.setDebug(false);
        subTable.left().add(collegeLogo).height(80).width(100).pad(0);
        subTable.right().add(playerNameLabel).pad(0);
        subTable.row();
        subTable.add(additionalInformationLabel).colspan(2);
        subTable.row();
        subTable.add(turnTimerLabel).colspan(2);

        table.add(subTable);

        return table;
    }

    /**
     * Sets the bar at the bottom of the HUD to the details of the sector currently hovered over
     * If no sector is being hovered then displays "Mouse over a sector to see further details"
     * @param sector the sector of details to be displayed
     */
    public void setBottomBarText(Sector sector) {
        if (sector == null) {
            this.bottomBarRightPart.setText("Mouse over a sector to see further details");
        } else {
            this.bottomBarRightPart.setText("College: " + map.getCollegeById(sector.getCollegeId()).getDisplayName() + " - " + sector.getDisplayName() + " - " + "Owned By: " + gameScreen.getPlayerById(sector.getOwnerId()).getPlayerName() + " - " + "Grants +" + sector.getReinforcementsProvided() + " Troops");
        }
    }

    void enterPhase(Player player, String additionalInformation) {
        this.currentPlayer = player;

        playerNameLabel.setText(new StringBuilder((CharSequence) currentPlayer.getPlayerName()));
        collegeLogo.setDrawable(WidgetFactory.genCollegeLogoDrawable(player.getCollegeName()));
        setAdditionalInformation(additionalInformation);
    }


    void setTimerValue(int timeRemaining) {
        turnTimerLabel.setText(new StringBuilder("Turn Timer: " + timeRemaining));
    }

    /**
     * sets the text displayed on the additional information label
     * @param additionalInformation
     */
    void setAdditionalInformation(String additionalInformation) {
        this.additionalInformationLabel.setText(additionalInformation);
    }

    /**
     * method for tidying up phase for next player to use
     */
    public void endPhase () {
        this.currentPlayer = null;
    }

    @Override
    public void act() {
        super.act();
    }

    @Override
    public void draw() {
        phaseAct();

        gameScreen.getGameplayBatch().begin();
        visualisePhase(gameScreen.getGameplayBatch());
        gameScreen.getGameplayBatch().end();

        super.draw();
    }

    public abstract void phaseAct();

    /**
     * abstract method for writing phase specific rendering
     * @param batch
     */
    protected abstract void visualisePhase(SpriteBatch batch);
}
