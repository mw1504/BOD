package sepr.game;

import com.badlogic.gdx.*;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import sepr.game.utils.PlayerType;
import sepr.game.utils.TurnPhaseType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;

/**
 * main class for controlling the game
 * implements screen for swapping what is being displayed with other screens, i.e. menu screens
 * input processor implemented to parse user input
 */
public class GameScreen implements Screen, InputProcessor{
    public static final int NEUTRAL_PLAYER_ID = 4;

    public AudioManager Audio = AudioManager.getInstance(); // Access to the AudioManager
    private Main main; // main stored for switching between screens

    private TurnPhaseType currentPhase = TurnPhaseType.REINFORCEMENT; // set initial phase to the reinforcement phase
    private PVC ProViceChancellor;
    private HashMap<TurnPhaseType, Phase> phases; // hashmap for storing the three phases of the game

    private SpriteBatch gameplayBatch; // sprite batch for rendering the game to
    private OrthographicCamera gameplayCamera; // camera for controlling what aspects of the game can be seen
    private Viewport gameplayViewport; // viewport for handling rendering the game at different resolutions

    private Map map; // stores state of the game: who owns which sectors
    private HashMap<Integer, Player> players; // player id mapping to the relevant player

    private HashMap<Integer, Boolean> keysDown; // mapping from key, (Input.Keys), to whether it has been pressed down

    // timer settings
    private boolean turnTimerEnabled;
    private int maxTurnTime;
    private long turnTimeStart;

    private List<Integer> turnOrder; // array of player ids in order of players' turns;
    private int currentPlayerPointer; // index of current player in turnOrder list
    private int previousPlayerPointer; // index of the previous player in turnOrder list

    private Texture mapBackground; // texture for drawing as a background behind the game

    private boolean gameSetup = false; // true once setupGame has been called

    private Random random;

    // pause menu setup - NEW Assessment 4
    private boolean timerPaused;
    private long pauseStartTime;
    private long pausedTime;

    /**
     * sets up rendering objects and key input handling
     * setupGame then start game must be called before a game is ready to be played
     *
     * @param main used to change screenthis.phases = phases;
     */

    public GameScreen(Main main) {
        this.main = main;

        this.gameplayBatch = new SpriteBatch();
        this.gameplayCamera = new OrthographicCamera();
        this.gameplayViewport = new ScreenViewport(gameplayCamera);

        this.mapBackground = new Texture("uiComponents/mapBackground.png");

        // setup hashmap to check which keys were previously pressed
        this.keysDown = new HashMap<Integer, Boolean>();
        this.keysDown.put(Input.Keys.UP, false);
        this.keysDown.put(Input.Keys.LEFT, false);
        this.keysDown.put(Input.Keys.DOWN, false);
        this.keysDown.put(Input.Keys.RIGHT, false);
        this.keysDown.put(Input.Keys.S, false);
        this.keysDown.put(Input.Keys.L, false);

        this.random = new Random();
    }

    public GameScreen(Main main, TurnPhaseType currentPhase, Map map, HashMap<Integer, Player> players, boolean turnTimerEnabled, int maxTurnTime, long turnTimeStart, List<Integer> turnOrder, int currentPlayerPointer){
        this(main);

        setUpPhases();

        Audio.loadSounds(); //loads the sounds into memory

        this.currentPhase = currentPhase;

        this.map = map;
        this.players = players;
        this.turnTimerEnabled = turnTimerEnabled;
        this.maxTurnTime = maxTurnTime;
        this.turnTimeStart = turnTimeStart;
        this.turnOrder = turnOrder;
        this.currentPlayerPointer = currentPlayerPointer;
        this.phases.get(this.currentPhase).enterPhase(getCurrentPlayer());
        this.ProViceChancellor = new PVC((float)1.00,this);
        this.gameSetup = true;
    }

    /**
     * sets up a new game
     * start game must be called before the game is ready to be played
     *
     * @param players HashMap of the players in this game
     * @param turnTimerEnabled should players turns be limited
     * @param maxTurnTime time elapsed in cthis.phases = phases;urrent turn, irrelevant if turn timer not enabled
     */
    public void setupGame(HashMap<Integer, Player> players, boolean turnTimerEnabled, int maxTurnTime, boolean allocateNeutralPlayer) {
        Audio.loadSounds(); //loads the sounds into memory
        this.players = players;
        this.turnOrder = new ArrayList<Integer>();
        for (Integer i : players.keySet()) {
            if ((players.get(i).getPlayerType() != PlayerType.NEUTRAL_AI)) { // don't add the neutral player or unassigned to the turn order
                this.turnOrder.add(i);
            }
        }


        this.currentPlayerPointer = 0; // set the current player to the player in the first position of the turnOrder list

        this.turnTimerEnabled = turnTimerEnabled;
        this.maxTurnTime = maxTurnTime;
        this.ProViceChancellor = new PVC((float)1.00,this);
        this.map = new Map(this.players, allocateNeutralPlayer, ProViceChancellor); // setup the game map and allocate the sectors

        setUpPhases();

        gameSetup = true; // game is now setup
    }

    public void setUpPhases(){
        // create the game phases and add them to the phases hashmap
        this.phases = new HashMap<TurnPhaseType, Phase>();
        this.phases.put(TurnPhaseType.REINFORCEMENT, new PhaseReinforce(this));
        this.phases.put(TurnPhaseType.ATTACK, new PhaseAttack(this));
        this.phases.put(TurnPhaseType.MOVEMENT, new PhaseMovement(this));
    }

    /**
     * called once game is setup to enter the first phase of the game; centre the game camera and start the turn timer
     *
     * @throws RuntimeException if this is called before the game is setup, i.e. setupGame has not been called before this
     */
    public void startGame() {
        if (!gameSetup) {
            throw new RuntimeException("Cannot start game before it is setup");
        }
        this.turnTimeStart = System.currentTimeMillis(); // set turn start time to current rime
        this.phases.get(currentPhase).enterPhase(getCurrentPlayer());
        resetCameraPosition();
    }

    /**
     * configure input so that input into the current phase's UI takes priority then unhandled input is handled by this class
     */
    private void updateInputProcessor() {
        InputMultiplexer inputMultiplexer = new InputMultiplexer();
        inputMultiplexer.addProcessor(phases.get(currentPhase));
        inputMultiplexer.addProcessor(this);
        Gdx.input.setInputProcessor(inputMultiplexer);
    }

    /**
     * checks if game is over by checking how many players are in the turn order, if 1 then player has won, if 0 then the neutral player has won
     *
     * @return true if game is over else false
     */
    private boolean isGameOver() {
        return turnOrder.size() <= 1; // game is over if only one player is in the turn order
    }

    /**
     * MODIFIED Assessment 4
     * gets in seconds the amount of time remaining of the current player's turn
     *
     * @return time remaining in turn in seconds
     */
    private int getTurnTimeRemaining(){
        return maxTurnTime - (int)((System.currentTimeMillis() - (turnTimeStart + pausedTime)) / 1000);
    }

    /**
     * NEW Assessment 4
     * records the time at which the timer was paused
     */
    public void pauseTimer(){
        this.pauseStartTime = System.currentTimeMillis();
        this.timerPaused = true;
    }

    /**
     * NEW Assessment 4
     * resumes the timer
     */
    public void resumeTimer(){
        pausedTime += (System.currentTimeMillis() - pauseStartTime);
        pauseStartTime = 0;
        this.timerPaused = false;
    }

    /**
     * returns the player object corresponding to the passed id from the players hashmap
     *
     * @param id of the player object that is wanted
     * @return the player whose id matches the given one in the players hashmap
     * @throws IllegalArgumentException if the supplied id is not a key value in the players hashmap
     */
    protected Player getPlayerById(int id) throws IllegalArgumentException {
        if (!players.containsKey(id)) throw new IllegalArgumentException("Cannot fetch player as id: " + id + " does not exist");
        return players.get(id);
    }


    /**
     *
     * @return gets the player object for the player who's turn it currently is
     */
    private Player getPreviousPlayer() {
        return players.get(turnOrder.get(previousPlayerPointer));
    }

    /**
     *
     * @return gets the player object for the player who's turn it currently is
     */
    private Player getCurrentPlayer() {
        return players.get(turnOrder.get(currentPlayerPointer));
    }

    /**
     *
     * @return the sprite batch being used to render the game
     */
    protected SpriteBatch getGameplayBatch() {
        return this.gameplayBatch;
    }

    /**
     *
     * @return the map object for this game
     */
    public Map getMap() {
        return map;
    }

    /**
     * method is used for progression through the phases of a turn evaluating the currentPhase case label
     * if nextPhase is called during the movement phase then the game progresses to the next players turn
     */
    protected void nextPhase() {
        this.phases.get(currentPhase).endPhase();



        switch (currentPhase) {
            case REINFORCEMENT:
                currentPhase = TurnPhaseType.ATTACK;
                break;
            case ATTACK:
                currentPhase = TurnPhaseType.MOVEMENT;
                break;
            case MOVEMENT:
                currentPhase = TurnPhaseType.REINFORCEMENT;

                nextPlayer(); // nextPhase called during final phase of a player's turn so goto next player

                break;
        }

        this.updateInputProcessor(); // phase changed so update input handling
        this.phases.get(currentPhase).enterPhase(getCurrentPlayer()); // setup the new phase for the current player
        removeEliminatedPlayers(); // check no players have been eliminated
    }

    /**
     * called when the player ends the MOVEMENT phase of their turn to advance the game to the next Player's turn
     * increments the currentPlayerPointer and resets it to 0 if it now exceeds the number of players in the list
     */
    private void nextPlayer() {
        previousPlayerPointer = currentPlayerPointer;
        currentPlayerPointer++;
        if (currentPlayerPointer == turnOrder.size()) { // reached end of players, reset to 0 and increase turn number
            currentPlayerPointer = 0;

        }

        resetCameraPosition(); // re-centres the camera for the next player

        if (this.turnTimerEnabled) { // if the turn timer is on reset it for the next player
            this.turnTimeStart = System.currentTimeMillis();
        }
    }

    /**
     * removes all players who have 0 sectors from the turn order
     */
    private void removeEliminatedPlayers() {
        List<Integer> playerIdsToRemove = new ArrayList<Integer>(); // list of players in the turn order who have 0 sectors
        for (Integer i : turnOrder) {
            boolean hasSector = false; // has a sector belonging to player i been found
            for (Integer j : map.getSectorIds()) {
                if (map.getSectorById(j).getOwnerId() == i) {
                    hasSector = true; // sector owned by player i found
                    break; // only need one sector to remain in turn order so can break once one found
                }
            }
            if (!hasSector) { // player has no sectors so remove them from the game
                playerIdsToRemove.add(i);
            }
        }

        if (playerIdsToRemove.size() > 0) { // if there are any players to remove
            turnOrder.removeAll(playerIdsToRemove);

            Audio.get("sound/Minigame/Colin_That_was_a_poor_performance.wav", Sound.class).play(AudioManager.GlobalFXvolume);

            String[] playerNames = new String[playerIdsToRemove.size()]; // array of names of players who have been removed
            for (int i = 0; i < playerIdsToRemove.size(); i++) {
                playerNames[i] = players.get(playerIdsToRemove.get(i)).getPlayerName();
            }

            DialogFactory.playersOutDialog(playerNames, phases.get(currentPhase)); // display which players have been eliminated
        }

        if (isGameOver()) { // check if game is now over
            gameOver();
        }
    }

    /**
     * method called when one player owns all the sectors in the map
     *
     * @throws RuntimeException if there is more than one player in the turn order when gameOver is called
     */
    private void gameOver() throws RuntimeException {
        if (turnOrder.size() == 0) { // neutral player has won
            DialogFactory.gameOverDialog(players.get(NEUTRAL_PLAYER_ID).getPlayerName(), players.get(NEUTRAL_PLAYER_ID).getCollegeName().getCollegeName(), main, phases.get(currentPhase));

        } else if (turnOrder.size() == 1){ // winner is player id at index 0 in turn order
            int voice = random.nextInt(4);

            switch (voice){
                case 0:
                    Audio.get("sound/Victory/Colin_Congratulations.wav", Sound.class).play(AudioManager.GlobalFXvolume);
                    break;
                case 1:
                    Audio.get("sound/Victory/Colin_Congratulations_your_grandson_would_be_proud_of_you.wav", Sound.class).play(AudioManager.GlobalFXvolume);
                    break;
                case 2:
                    Audio.get("sound/Victory/Colin_Well_Done.wav", Sound.class).play(AudioManager.GlobalFXvolume);
                    break;
                case 3:
                    Audio.get("sound/Victory/Colin_You_are_victorious.wav", Sound.class).play(AudioManager.GlobalFXvolume);
                    break;
                case 4:
                    break;
            }

            Audio.get("", Sound.class).play(AudioManager.GlobalFXvolume);
            
            int winnerId = turnOrder.get(0); // winner will be the only player in the turn order list
            DialogFactory.gameOverDialog(players.get(winnerId).getPlayerName(), players.get(winnerId).getCollegeName().getCollegeName(), main, phases.get(currentPhase));

        } else { // more than one player in turn order so no winner found therefore throw error
            throw new RuntimeException("Game Over called but more than one player in turn order");
        }
    }

    /**
     * moves the camera in the appropriate direction if the corresponding arrow key is down
     */
    private void controlCamera() {
        if (this.keysDown.get(Input.Keys.UP)) {
            this.gameplayCamera.translate(0, 4, 0);
        }
        if (this.keysDown.get(Input.Keys.DOWN)) {
            this.gameplayCamera.translate(0, -4, 0);
        }
        if (this.keysDown.get(Input.Keys.LEFT)) {
            this.gameplayCamera.translate(-4, 0, 0);
        }
        if (this.keysDown.get(Input.Keys.RIGHT)) {
            this.gameplayCamera.translate(4, 0, 0);
        }

    }

    /**
     * re-centres the camera and sets the zoom level back to default
     */
    public void resetCameraPosition() {
        this.gameplayCamera.position.x = 1920/2;
        this.gameplayCamera.position.y = 1080/2;
        this.gameplayCamera.zoom = 1;
    }

    /**
     * converts a point on the screen to a point in the world
     *
     * @param screenX x coordinate of point on screen
     * @param screenY y coordinate of point on screen
     * @return the corresponding world coordinates
     */
    public Vector2 screenToWorldCoords(int screenX, int screenY) {
        float x = gameplayCamera.unproject(new Vector3(screenX, screenY, 0)).x;
        float y = gameplayCamera.unproject(new Vector3(screenX, screenY, 0)).y;
        return new Vector2(x, y);
    }

    /**
     * MODIFIED Assessment 4
     * changes the screen currently being displayed to the miniGame
     */
    public void openMiniGame() {
        this.pauseTimer();
        main.setMiniGameScreen();
    }

    /**
     * changes the screen currently being displayed to the menu
     */
    public void openMenu() {
        Audio.disposeMusic("sound/Gameplay Music/wind.mp3"); //remove game play sounds from memory to save space
        Audio.loadMusic("sound/IntroMusic/introMusic.mp3"); //load and play main menu music
        main.setMenuScreen();
    }
    /**
     * draws a background image behind the map and UI covering the whole visible area of the render window
     */
    private void renderBackground() {
        Vector3 mapDrawPos = gameplayCamera.unproject(new Vector3(0, Gdx.graphics.getHeight(), 0));
        gameplayBatch.draw(mapBackground, mapDrawPos.x, mapDrawPos.y, gameplayCamera.viewportWidth * gameplayCamera.zoom, gameplayCamera.viewportHeight * gameplayCamera.zoom );
    }

    /* Screen implementation */

    /**
     * when this screen is shown updates the input handling so it is from this screen
     */
    @Override
    public void show() {
        this.updateInputProcessor();
    }

    /**
     * updates the game and renders it to the screen
     *
     * @param delta time elapsed between this and the previous update in seconds
     * @throws RuntimeException when method called before the game is setup
     */
    @Override
    public void render(float delta) {
        if (!gameSetup) throw new RuntimeException("Game must be setup before attempting to play it"); // throw exception if attempt to run game before its setup

        this.controlCamera(); // move camera

        gameplayCamera.update();
        gameplayBatch.setProjectionMatrix(gameplayCamera.combined);

        gameplayBatch.begin(); // begin rendering

        renderBackground(); // draw the background of the game
        map.draw(gameplayBatch); // draw the map

        gameplayBatch.end(); // stop rendering

        if (this.turnTimerEnabled) { // update the timer display, if it is enabled
            this.phases.get(currentPhase).setTimerValue(getTurnTimeRemaining());
        }
        this.phases.get(currentPhase).act(delta); // update the stage of the current phase
        this.phases.get(currentPhase).draw(); // draw the phase UI

        if (this.turnTimerEnabled && (getTurnTimeRemaining() <= 0)) { // goto the next player's turn if the timer is enabled and they have run out of time
            nextPlayer();
        }
    }

    @Override
    public void resize(int width, int height) {
        for (Stage stage : phases.values()) { // update the rendering properties of each stage when the screen is resized
            stage.getViewport().update(width, height);
            stage.getCamera().viewportWidth = width;
            stage.getCamera().viewportHeight = height;
            stage.getCamera().position.x = width/2;
            stage.getCamera().position.y = height/2;
            stage.getCamera().update();
        }

        // update this classes rending properties for the new display size
        this.gameplayViewport.update(width, height);
        this.gameplayCamera.viewportWidth = width;
        this.gameplayCamera.viewportHeight = height;
        this.gameplayCamera.translate(1920/2, 1080/2, 0);
        this.gameplayCamera.update();
    }

    // MODIFIED Assessment 4
    @Override
    public void pause() {
        this.pauseTimer();
    }

    // MODIFIED Assessment 4
    @Override
    public void resume() {
        this.resumeTimer();
    }

    @Override
    public void hide() {

    }

    @Override
    public void dispose() {

    }
    /* Input Processor implementation */

    @Override
    public boolean keyDown(int keycode) {
        if (keycode == Input.Keys.UP) {
            keysDown.put(Input.Keys.UP, true);
        }
        if (keycode == Input.Keys.DOWN) {
            keysDown.put(Input.Keys.DOWN, true);
        }
        if (keycode == Input.Keys.LEFT) {
            keysDown.put(Input.Keys.LEFT, true);
        }
        if (keycode == Input.Keys.RIGHT) {
            keysDown.put(Input.Keys.RIGHT, true);
        }
        return true;
    }

    @Override
    public boolean keyUp(int keycode) {
        if (keycode == Input.Keys.UP) {
            keysDown.put(Input.Keys.UP, false);
        }
        if (keycode == Input.Keys.DOWN) {
            keysDown.put(Input.Keys.DOWN, false);
        }
        if (keycode == Input.Keys.LEFT) {
            keysDown.put(Input.Keys.LEFT, false);
        }
        if (keycode == Input.Keys.RIGHT) {
            keysDown.put(Input.Keys.RIGHT, false);
        }
        if (keycode == Input.Keys.ESCAPE) {
            DialogFactory.leaveGameDialogBox(this, phases.get(currentPhase)); // confirm if the player wants to leave if escape is pressed
        }
        if (keycode == Input.Keys.S) {
            this.main.SaveGame();
        }
        if (keycode == Input.Keys.L) {
            this.main.LoadGame();
        }
        return true;
    }

    @Override
    public boolean keyTyped(char character) {
        return false;
    }

    @Override
    public boolean touchDown(int screenX, int screenY, int pointer, int button) {
        return false;
    }

    @Override
    public boolean touchUp(int screenX, int screenY, int pointer, int button) {
        return false;
    }

    @Override
    public boolean touchDragged(int screenX, int screenY, int pointer) {
        return false;
    }

    @Override
    public boolean mouseMoved(int screenX, int screenY) {
        Vector2 worldCoords = screenToWorldCoords(screenX, screenY);

        int hoveredSectorId = map.detectSectorContainsPoint((int)worldCoords.x, (int)worldCoords.y); // get id of sector mouse is currently hovered over
        if (hoveredSectorId == -1) {
            phases.get(currentPhase).setBottomBarText(null); // no sector hovered over: update bottom bar with null sector
        } else {
            phases.get(currentPhase).setBottomBarText(map.getSectorById(hoveredSectorId)); // update the bottom bar of the UI with the details of the sector currently hovered over by the mouse
        }
        return false;
    }

    @Override
    public boolean scrolled(int amount) {
        if ((gameplayCamera.zoom > 0.5 && amount < 0) || (gameplayCamera.zoom < 1.5 && amount > 0)) { // if the mouse scrolled zoom in/out
            gameplayCamera.zoom += amount * 0.03f;
        }
        return true;
    }

    public TurnPhaseType getCurrentPhase(){
        return this.currentPhase;
    }

    public HashMap<Integer, Player> getPlayers() {
        return players;
    }

    public boolean isTurnTimerEnabled(){
        return this.turnTimerEnabled;
    }

    public int getMaxTurnTime(){
        return this.maxTurnTime;
    }

    public long getTurnTimeStart(){
        return this.turnTimeStart;
    }

    public List<Integer> getTurnOrder(){
        return this.turnOrder;
    }

    public int getCurrentPlayerPointer(){
        return this.currentPlayerPointer;
    }
}