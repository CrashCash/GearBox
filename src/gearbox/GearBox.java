package gearbox;

import com.sun.j3d.utils.applet.*;
import com.sun.j3d.utils.behaviors.vp.OrbitBehavior;
import com.sun.j3d.utils.image.*;
import com.sun.j3d.utils.universe.*;
import java.awt.*;
import java.awt.event.*;
import java.net.URL;
import javax.media.j3d.*;
import javax.swing.*;
import javax.vecmath.*;

// Simulate a shifting gears in a sequential motorcycle transmission.
// Ported to pure Java 19-DEC-2009 from the version written in Jython on 10-OCT-2004
// Created NetBeans project 09-MAY-2017
public class GearBox extends JApplet {
    static final double two_pi = 2.0 * Math.PI;

    // physical parameters for model
    double shaftOffset = 0.5;
    double[] shaftRadius = {0.15, 0.15, 0.4, 0.1, 0.1};
    double shaftLength = 3.5;
    double valleyToCircPitchRatio = 0.15;
    double addendum = 0.05;
    double dedendum = 0.05;
    double gearThickness = 0.3;
    double toothTipThickness = gearThickness - 0.05;
    double indexCamWidth = shaftLength * 0.05;

    // centers of input shaft, output shaft, shift cam, shift fork shafts
    Vector3d[] shaftPlacement = {new Vector3d(-shaftOffset, 0.0, 0.0),
                                 new Vector3d(shaftOffset, 0.0, 0.0),
                                 new Vector3d(0.0, -2.5 * shaftOffset, 0.0),
                                 new Vector3d(-shaftOffset * 1.25, -2.0 * shaftOffset, 0.0),
                                 new Vector3d(shaftOffset * 1.25, -2.0 * shaftOffset, 0.0)};

    // teeth on 1st through 6th gear on each of the 2 shafts (taken from 2002 Suzuki SV-650
    // technical manual)
    final int[][] gearTeeth = {{13, 18, 21, 24, 26, 27}, {32, 32, 29, 27, 25, 23}};

    // gear ratios
    double[][] gearRatios = new double[gearTeeth.length][gearTeeth[0].length];

    // gear dogs flags
    final Gear.Dogs[][] dogs = {{Gear.Dogs.NONE, Gear.Dogs.NONE, Gear.Dogs.FRONT, Gear.Dogs.REAR,
                                 Gear.Dogs.NONE, Gear.Dogs.NONE},
                                {Gear.Dogs.NONE, Gear.Dogs.NONE, Gear.Dogs.NONE, Gear.Dogs.NONE,
                                 Gear.Dogs.BOTH, Gear.Dogs.BOTH}};

    // EnumMap for gearMetals is just too clunky, so this can't be an enum
    int GEARTYPE_SPIN = 0; // gear spins freely on shaft but does not slide
    int GEARTYPE_SLIDE = 1; // gear slides along shaft but does not spin
    int GEARTYPE_FIXED = 2; // gear firmly fixed to shaft
    int[][] gearTypes = {{GEARTYPE_FIXED, GEARTYPE_FIXED, GEARTYPE_SLIDE, GEARTYPE_SLIDE,
                          GEARTYPE_SPIN, GEARTYPE_SPIN},
                         {GEARTYPE_SPIN, GEARTYPE_SPIN, GEARTYPE_SPIN, GEARTYPE_SPIN, GEARTYPE_SLIDE,
                          GEARTYPE_SLIDE}};

    // note that gear pairs are placed on shaft in the order: 1st, 5th, 4th, 3rd, 6th, 2nd
    double[] gearPlacement = {-1.25, 1.25, 0.25, -0.25, -0.75, 0.75};

    // gear position movements in each "gear" selection
    int[][] shiftMatrix = {{0, -1, 0}, {0, 0, 0}, {0, 0, 1}, {0, 0, -1}, {0, 1, 0}, {-1, 0, 0},
                           {1, 0, 0}};

    // descriptions of movement during upshift and downshift.
    String[][] shiftDescr = {{"5th gear moves to pin 1st gear", ""},
                             {"6th gear moves away from 2nd gear",
                              "5th gear moves away from 1st gear"},
                             {"6th gear moves from 3rd gear to pin 2nd gear",
                              "6th gear moves to pin 2nd gear"},
                             {"6th gear moves away from 4th gear, 6th gear moves to pin 3rd gear",
                              "6th gear moves from 2nd gear to pin 3rd gear"},
                             {"3rd/4th moves away from 5th gear, 5th gear moves to pin 4th gear",
                              "6th gear moves away from 3rd gear, 5th gear moves to pin 4th gear"},
                             {"3rd/4th moves from 6th gear to pin 5th gear",
                              "5th gear moves away from 4th gear, 3rd/4th moves to pin 5th gear"},
                             {"", "3rd/4th moves from 5th gear to pin 6th gear"}};

    // indexes of sliding gears
    int[][] slidingGears = {{0, 2}, {1, 4}, {1, 5}};

    // metal materials for gears, shafts, cam
    float[][] metalsColors = {{0.5F, 0.5F, 0.6F, 120.0F}, // Metallic silver
                              {0.5F, 0.1F, 0.1F, 100.0F}, // Dark red
                              {0.1F, 0.5F, 0.1F, 100.0F}, // Dark green
                              {0.8F, 0.6F, 0.8F, 100.0F}, // Pink
                              {0.8F, 0.8F, 0.0F, 90.0F}, // Yellow
                              {0.8F, 0.8F, 0.8F, 60.0F}}; // Off-white

    // textures for each metal
    Appearance[] metals = new Appearance[metalsColors.length];

    // assign Gear colors according to type (silver for spinning, pink for sliding,
    // yellow for fixed)
    int[] gearMetals = {0, 3, 4};

    // metals for each particular gear
    Appearance gearMetalsIndex[][] = new Appearance[gearTeeth.length][gearTeeth[0].length];

    // input/drive shaft is red, output/driven shaft is green, fork shafts are silver,
    // shift cam is texture-mapped
    int[] shaftMetals = {2, 1, 3, 0, 0};

    // starting speed
    long inputRPM = 5000;

    // root of scene graph
    BranchGroup branchRoot;

    // bounds for universe
    BoundingSphere bounds;

    // status display
    JLabel shiftDescLabel;

    // gear position display
    JLabel shiftPosLabel;

    // input RPM display
    JLabel inputRPMLabel;

    // output RPM display
    JLabel outputRPMLabel;

    // gear geometry objects
    Gear gears[][] = new Gear[gearTeeth.length][gearTeeth[0].length];

    // an "alpha" provides a value of 0 to 1 over a specific time period and repetition count,
    // so they govern speed of movement
    // gear rotational alpha values
    Alpha[][] gearAlphas = new Alpha[gearTeeth.length][gearTeeth[0].length];

    // input shaft rotational alpha value
    Alpha inputAlpha = new Alpha();

    // output shaft rotational alpha value
    Alpha outputAlpha = new Alpha();

    // shift cam alpha
    Alpha shiftAlpha = new Alpha();

    // interpolators move an object along a path based on the value of an alpha
    PositionInterpolator gearInterp[] = new PositionInterpolator[slidingGears.length];
    PositionInterpolator shiftForkInterp[] = new PositionInterpolator[3];
    CamInterpolator camRotor;

    // current shift state
    int gearPosition = 1;
    int oldGearPosition = 1;
    double currentAngle = 30;
    int gearPair = -1;

    // integrate the 3D window into a Swing window, with a control panel at bottom
    public GearBox() {
        // Create canvas holding rendering
        setLayout(new BorderLayout());
        GraphicsConfiguration config = SimpleUniverse.getPreferredConfiguration();
        Canvas3D canvas3D = new Canvas3D(config);
        add("Center", canvas3D);

        // top line of control panel
        JPanel panelTop = new JPanel(new GridBagLayout());
        GridBagConstraints c;

        // input RPM label
        inputRPMLabel = new JLabel();
        inputRPMLabel.setPreferredSize(new java.awt.Dimension(150, 15));
        panelTop.add(inputRPMLabel, new GridBagConstraints());

        // up-shift button
        JButton btn1 = new JButton("Up-shift");
        btn1.setPreferredSize(new java.awt.Dimension(120, 25));
        btn1.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                upshift();
            }
        });
        c = new GridBagConstraints();
        c.anchor = GridBagConstraints.EAST;
        c.weightx = 1.0;
        panelTop.add(btn1, c);

        // gear shift position label
        shiftPosLabel = new JLabel();
        shiftPosLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        c = new GridBagConstraints();
        c.ipadx = 40;
        panelTop.add(shiftPosLabel, c);

        // down-shift button
        JButton btn2 = new JButton("Down-shift");
        btn2.setPreferredSize(new java.awt.Dimension(120, 25));
        btn2.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                downshift();
            }
        });
        c = new GridBagConstraints();
        c.anchor = java.awt.GridBagConstraints.WEST;
        c.weightx = 1.0;
        panelTop.add(btn2, c);

        // animation checkbox
        JCheckBox cb1 = new JCheckBox("Animation");
        cb1.setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);
        cb1.setPreferredSize(new java.awt.Dimension(150, 23));
        cb1.addItemListener(new ItemListener() {
            public void itemStateChanged(ItemEvent e) {
                animateShafts(e);
            }
        });
        panelTop.add(cb1, new java.awt.GridBagConstraints());

        // bottom line of control panel
        JPanel panelBot = new JPanel(new GridBagLayout());

        // output RPM label
        outputRPMLabel = new JLabel();
        outputRPMLabel.setPreferredSize(new java.awt.Dimension(200, 15));
        panelBot.add(outputRPMLabel, new GridBagConstraints());

        // description in center
        shiftDescLabel = new JLabel();
        c = new GridBagConstraints();
        c.weightx = 1.0;
        panelBot.add(shiftDescLabel, c);

        // animation speed checkbox
        JCheckBox cb2 = new JCheckBox("Fast Anim");
        cb2.addItemListener(new ItemListener() {
            public void itemStateChanged(ItemEvent e) {
                animateSpeed(e);
            }
        });
        cb2.setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);
        cb2.setPreferredSize(new java.awt.Dimension(200, 23));
        panelBot.add(cb2, new GridBagConstraints());

        // now install subpanels in bottom of display
        JPanel panel = new JPanel(new GridBagLayout());
        c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        panel.add(panelTop, c);

        c = new GridBagConstraints();
        c.gridy = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        panel.add(panelBot, c);

        add("South", panel);

        // create the root of the scene graph
        branchRoot = new BranchGroup();

        // create a bounds for the background and lights
        bounds = new BoundingSphere(new Point3d(0.0, 0.0, 0.0), 100.0);

        // set up the background
        Color3f bgColor = new Color3f(0.1F, 0.1F, 0.5F);
        Background bgNode = new Background(bgColor);
        bgNode.setApplicationBounds(bounds);
        branchRoot.addChild(bgNode);

        // set up the ambient light
        out("Set up lights");
        Color3f ambientColor = new Color3f(0.1F, 0.1F, 0.1F);
        AmbientLight ambientLightNode = new AmbientLight(ambientColor);
        ambientLightNode.setInfluencingBounds(bounds);
        branchRoot.addChild(ambientLightNode);

        // set up the directional lights
        Color3f light1Color = new Color3f(1.0F, 1.0F, 1.0F);
        Vector3f light1Direction = new Vector3f(1.0F, 1.0F, 1.0F);
        DirectionalLight light1 = new DirectionalLight(light1Color, light1Direction);
        light1.setInfluencingBounds(bounds);
        branchRoot.addChild(light1);

        Color3f light2Color = new Color3f(1.0F, 1.0F, 1.0F);
        Vector3f light2Direction = new Vector3f(-1.0F, -1.0F, -1.0F);
        DirectionalLight light2 = new DirectionalLight(light2Color, light2Direction);
        light2.setInfluencingBounds(bounds);
        branchRoot.addChild(light2);

        // create the gearbox
        createGearBox();

        // create a universe on the canvas and attach the scene graph
        SimpleUniverse u = new SimpleUniverse(canvas3D);
        u.addBranchGraph(branchRoot);

        // set viewing platform transforms for initial viewing angle & distance
        out("Setting initial view");
        ViewingPlatform viewingPlatform = u.getViewingPlatform();
        TransformGroup vpTrans = viewingPlatform.getViewPlatformTransform();
        Transform3D tempTransform = new Transform3D();
        tempTransform.lookAt(new Point3d(-3.0, 3.0, 5.0), new Point3d(0.0, -0.5, 0.0),
                new Vector3d(0.0, 1.0, 0.0));
        tempTransform.normalize();
        tempTransform.invert();
        vpTrans.setTransform(tempTransform);

        // add mouse behaviours to the viewing platform
        out("Adding mouse behaviours");
        OrbitBehavior orbit = new OrbitBehavior(canvas3D, OrbitBehavior.REVERSE_ALL |
                                                          OrbitBehavior.STOP_ZOOM);
        orbit.setSchedulingBounds(bounds);
        viewingPlatform.setViewPlatformBehavior(orbit);

        // set initial gear position to neutral
        out("Set inital gear position to neutral");
        shift();

        // pause everything at the same time so gears stay meshed properly
        animateShafts(false);
        out("Initialization complete");
    }

    // display status messages to the user.
    public void out(String msg) {
        System.out.println(msg);
        this.shiftDescLabel.setText(msg);
    }

    // do all the heavy lifting of creating all the geometries.
    public void createGearBox() {
        out("Creating gearbox");

        // create material appearances
        out("Create materials");
        Color3f black = new Color3f(0.0F, 0.0F, 0.0F);
        Color3f white = new Color3f(1.0F, 1.0F, 1.0F);
        for (int i = 0; i < metalsColors.length; i++) {
            Appearance metal = new Appearance();
            Color3f objColor = new Color3f(metalsColors[i]);
            metal.setMaterial(new Material(objColor, black, objColor, white, metalsColors[i][3]));
            metals[i] = metal;
        }

        // load texture from file for cam
        Appearance cam_texturemap = new Appearance();
        URL url = this.getClass().getResource("cam_texture.gif");
        Texture texture = new TextureLoader(url, this).getTexture();
        cam_texturemap.setTexture(texture);
        Color3f objColor = new Color3f(0.5F, 0.5F, 0.6F);
        cam_texturemap.setMaterial(new Material(objColor, black, objColor, white, 80.0F));

        // create the shafts
        Shaft[] shafts = new Shaft[shaftPlacement.length];
        TransformGroup[] shaftTGs = new TransformGroup[shaftPlacement.length];
        Transform3D tempTransform = new Transform3D();
        TransformGroup tg;
        Shaft s;
        for (int i = 0; i < shaftPlacement.length; i++) {
            out("Creating shaft " + String.valueOf(i + 1));
            if (i < 2) {
                // Main gear shafts are made in quarters so we can see shaft rotate
                s = new Shaft(shaftRadius[i], shaftLength, 15, metals[shaftMetals[i]],
                        metals[shaftMetals[i]], false, Math.PI / 2.0, 0.0, false);
                s.section(metals[shaftMetals[i]], metals[shaftMetals[i]], Math.PI / 2.0, Math.PI);
                s.section(metals[5], metals[5], Math.PI / 2.0, Math.PI * 0.5);
                s.section(metals[5], metals[5], Math.PI / 2.0, Math.PI * 1.5);
            } else if (i == 2) {
                // Shift cam is textured
                s = new Shaft(shaftRadius[i], shaftLength, 30, cam_texturemap, metals[5], true,
                        two_pi, 0.0, false);
                // Add indexing cam on end.
                s
                        .addChild(new StarCam(shaftRadius[i] * 1.25, 90, metals[2], shaftLength / 2,
                                indexCamWidth));
            } else // Shift fork shafts
            {
                s = new Shaft(shaftRadius[i], shaftLength, 15, metals[shaftMetals[i]],
                        metals[shaftMetals[i]], false, two_pi, 0.0, false);
            }
            shafts[i] = s;
            tg = new TransformGroup();
            shaftTGs[i] = tg;
            branchRoot.addChild(tg);
            tg.getTransform(tempTransform);
            tempTransform.setTranslation(shaftPlacement[i]);
            tg.setTransform(tempTransform);
            tg.addChild(s);
            if (i == 2) // Shift cam can rotate
            {
                tg.setCapability(TransformGroup.ALLOW_TRANSFORM_WRITE);
            }
        }
        // spin input shaft by animation
        Transform3D spinTrans = new Transform3D();
        spinTrans.rotX(Math.PI / 2.0);
        inputAlpha.setIncreasingAlphaDuration(inputRPM);
        RotationInterpolator shaft0Rotor = new RotationInterpolator(inputAlpha, shafts[0],
                spinTrans, 0.0F, (float) -two_pi);
        shaft0Rotor.setSchedulingBounds(bounds);
        shaftTGs[0].addChild(shaft0Rotor);

        // spin output shaft by animation
        RotationInterpolator shaft1Rotor = new RotationInterpolator(outputAlpha, shafts[1],
                spinTrans, 0.0F, (float) two_pi);
        shaft1Rotor.setSchedulingBounds(bounds);
        shaftTGs[1].addChild(shaft1Rotor);

        // indexing cam follower
        double followerDiameter = 0.06;
        Shaft follower = new Shaft(followerDiameter, shaftLength * 0.05, 30, metals[1], metals[1],
                false, two_pi, 0.0, false);
        tg = new TransformGroup();
        branchRoot.addChild(tg);
        tg.addChild(follower);
        tg.setCapability(TransformGroup.ALLOW_TRANSFORM_WRITE);

        // spin shift cam by animation
        shiftAlpha.setIncreasingAlphaDuration(800);
        camRotor = new CamInterpolator(shiftAlpha, shafts[2], spinTrans, 0.0, two_pi, tg,
                shaftRadius[2] * 1.25 + followerDiameter, -2.5 * shaftOffset,
                (shaftLength + indexCamWidth) / 2);
        camRotor.setSchedulingBounds(bounds);
        shaftTGs[2].addChild(camRotor);
        shiftAlpha.setLoopCount(1);

        // create the gears
        double gearRadius[][] = new double[gearTeeth.length][gearTeeth[0].length];
        TransformGroup gearTGs[][] = new TransformGroup[gearTeeth.length][gearTeeth[0].length];
        Vector3d v;
        for (int i = 0; i < gearTeeth.length; i++) {
            for (int j = 0; j < gearTeeth[i].length; j++) {
                out("Creating gear " + String.valueOf(j + 1) + " on shaft " + String.valueOf(i + 1));
                // Determine gear radius based on gear laws
                double m = 2 * shaftOffset / (gearTeeth[i][j] + gearTeeth[1 - i][j]);
                double r = m * gearTeeth[i][j];
                gearRadius[i][j] = r;
                gearRatios[i][j] = ((double) gearTeeth[i][j]) / (double) gearTeeth[1 - i][j];

                // create gear
                Appearance metalIndex = metals[gearMetals[gearTypes[i][j]]];
                gearMetalsIndex[i][j] = metalIndex;
                Gear g = new Gear(gearTeeth[i][j], r, shaftRadius[i], addendum, dedendum,
                        gearThickness, toothTipThickness, valleyToCircPitchRatio, metalIndex,
                        dogs[i][j]);
                gears[i][j] = g;
                tg = new TransformGroup();
                if (gearTypes[i][j] != GEARTYPE_FIXED) {
                    // gears can move
                    tg.setCapability(TransformGroup.ALLOW_TRANSFORM_WRITE);
                }
                tg.getTransform(tempTransform);
                tg.addChild(g);
                // place along shaft
                v = new Vector3d(0.0, 0.0, gearPlacement[j]);
                if (i == 0 && j == 3) {
                    // note that 4th driving gear is a special case, it's physically attached to
                    // the 3rd driving gear, so it's a child of 3rd gear instead of a child of the
                    // driving shaft. This is so they move together properly when shifting.
                    gears[0][2].addChild(tg);
                    v = new Vector3d(0.0, 0.0, -0.5);
                } else if (gearTypes[i][j] == GEARTYPE_SPIN) {
                    // set things up so spinning gears do so properly
                    v.add(shaftPlacement[i]);
                    branchRoot.addChild(tg);
                    Alpha alpha = new Alpha();
                    int sign = 2 * i - 1;
                    RotationInterpolator rotor = new RotationInterpolator(alpha, g, spinTrans, 0.0F,
                            (float) two_pi * sign);
                    rotor.setSchedulingBounds(bounds);
                    tg.addChild(rotor);
                    gearAlphas[i][j] = alpha;
                } else {
                    // fixed/sliding gears rotate with shaft
                    shafts[i].addChild(tg);
                }
                // rotate a little so teeth mesh properly
                if (i == 1 && gearTeeth[i][j] % 2 == 0) {
                    tempTransform.rotZ(g.getCircularPitchAngle() / -2);
                }
                tempTransform.setTranslation(v);
                tg.setTransform(tempTransform);
                gearTGs[i][j] = tg;
            }
        }

        // driving gears 3 & 4 are joined by a short shaft
        double length = gearPlacement[3] - gearPlacement[4];
        s = new Shaft(shaftRadius[0] * 1.25, length, 25, metals[3], null, false, two_pi, 0.0, false);
        tg = new TransformGroup();
        gears[0][3].addChild(tg);
        tg.getTransform(tempTransform);
        v = new Vector3d(0.0, 0.0, length / 2.0);
        tempTransform.setTranslation(v);
        tg.setTransform(tempTransform);
        tg.addChild(s);

        // animate gear movement during shifting
        Transform3D trans = new Transform3D();
        trans.rotY(Math.PI / -2.0);
        for (int i = 0; i < slidingGears.length; i++) {
            int x = slidingGears[i][0];
            int y = slidingGears[i][1];
            PositionInterpolator pos_interp = new PositionInterpolator(shiftAlpha, gears[x][y],
                    trans, 0.0F, 0.0F);
            pos_interp.setSchedulingBounds(bounds);
            gearTGs[x][y].addChild(pos_interp);
            gearInterp[i] = pos_interp;
        }

        // create shift forks
        // forkInfo is: index of shift shaft in shaftPlacement,
        //              index of main shaft in shaftPlacement,
        //              index of associated gear in gearPlacement
        int forkInfo[][] = {{3, 0, 3}, {4, 1, 4}, {4, 1, 5}};
        ShiftFork shiftForks[] = new ShiftFork[3];
        double forkThickness = 0.1;
        Vector3d horiz = new Vector3d(0.0, 1.0, 0.0);
        trans = new Transform3D();
        trans.rotY(Math.PI / -2.0);
        int forkPinSigns[] = {1, -1, -1};
        double offset;
        for (int i = 0; i < 3; i++) {
            out("Creating shift fork " + String.valueOf(i + 1));
            // compute angle from shift shaft to shift cam
            Vector3d tempVector = new Vector3d();
            tempVector.sub(shaftPlacement[forkInfo[i][1]], shaftPlacement[2]);
            double angle = horiz.angle(tempVector);
            // compute vector from main shaft to shift shaft
            tempVector.sub(shaftPlacement[forkInfo[i][0]], shaftPlacement[forkInfo[i][1]]);
            // create shift fork and TransformGroup
            ShiftFork sf = new ShiftFork(gearRadius[slidingGears[i][0]][slidingGears[i][1]] * 0.6,
                    shaftRadius[forkInfo[i][0]] * 1.25, tempVector, forkThickness, metals[4], angle,
                    forkPinSigns[i]);
            shiftForks[i] = sf;
            tg = new TransformGroup();
            branchRoot.addChild(tg);
            tg.setCapability(TransformGroup.ALLOW_TRANSFORM_WRITE);
            // move to one side of gear
            tempTransform = new Transform3D();
            if (i == 0) {
                offset = 0;
            } else {
                offset = gearPlacement[forkInfo[i][2]] + (gearThickness + forkThickness) / 2.0;
            }
            tempVector.add(shaftPlacement[forkInfo[i][1]], new Vector3d(0.0, 0.0, offset));
            tempTransform.setTranslation(tempVector);
            tg.setTransform(tempTransform);
            tg.addChild(sf);
            // animate fork movement during shifting
            PositionInterpolator pos_interp = new PositionInterpolator(shiftAlpha, sf, trans, 0.0F,
                    0.0F);
            pos_interp.setSchedulingBounds(bounds);
            tg.addChild(pos_interp);
            shiftForkInterp[i] = pos_interp;
        }

        // perform optimizations on this scene graph
        branchRoot.compile();

        // set all alphas to the same time so that gears mesh properly
        long t = System.currentTimeMillis() + 25;
        inputAlpha.setStartTime(t);
        outputAlpha.setStartTime(t);
        for (int i = 0; i < gearAlphas.length; i++) {
            for (int j = 0; j < gearAlphas[0].length; j++) {
                if (gearAlphas[i][j] != null) {
                    gearAlphas[i][j].setStartTime(t);
                }
            }
        }

        // we're done, stick it with a fork!
        out("Gearbox created");
    }

    // update things for current gear position
    public void shift() {
        int shiftCamAngle;
        double currentRatio;

        // restore gear colors
        if (gearPair != -1) {
            gears[0][gearPair].getSubShape().setAppearance(gearMetalsIndex[0][gearPair]);
            gears[1][gearPair].getSubShape().setAppearance(gearMetalsIndex[1][gearPair]);
        }
        // update gear position label, determine shift cam position, current gear ratio
        if (gearPosition == 0) {
            shiftPosLabel.setText("1");
            shiftCamAngle = 0;
            currentRatio = gearRatios[1][0];
            gearPair = 0;
        } else if (gearPosition == 1) {
            shiftPosLabel.setText("N");
            shiftCamAngle = 30; // neutral is halfway between first and second
            currentRatio = 0.0; // not transmitting power
            gearPair = -1; // no gear pairs in use
        } else {
            shiftPosLabel.setText(String.valueOf(gearPosition));
            gearPair = gearPosition - 1;
            // Turn cam 60 degrees for each gear
            shiftCamAngle = (gearPair) * 60;
            currentRatio = gearRatios[1][gearPair];
        }

        // color the gear pair that's transmitting power
        if (gearPair != -1) {
            gears[0][gearPair].getSubShape().setAppearance(metals[1]);
            gears[1][gearPair].getSubShape().setAppearance(metals[1]);
        }

        // move gears according to shift position matrix
        for (int i = 0; i < slidingGears.length; i++) {
            float pos = (float) (gearThickness * shiftMatrix[oldGearPosition][i] / 2.0);
            gearInterp[i].setStartPosition(pos);
            shiftForkInterp[i].setStartPosition(pos);
            pos = (float) (gearThickness * shiftMatrix[gearPosition][i] / 2.0);
            gearInterp[i].setEndPosition(pos);
            shiftForkInterp[i].setEndPosition(pos);
        }

        // move shift cam smoothly using animation rotation angles according to gear
        camRotor.setMinimumAngle((float) Math.toRadians(currentAngle + 45));
        camRotor.setMaximumAngle((float) Math.toRadians(shiftCamAngle + 45));
        currentAngle = shiftCamAngle;
        shiftAlpha.setStartTime(System.currentTimeMillis() + 25);

        // set speed of output shaft
        // to keep it from jumping, we have to determine a new start time so that the alpha is at the same value
        double outputRPM = (double) inputRPM * (double) currentRatio;
        outputAlpha.setStartTime((long) (System.currentTimeMillis() -
                                         (outputAlpha.value() * outputRPM)));
        outputAlpha.setIncreasingAlphaDuration((long) outputRPM);

        // set speed of individual spinning gears
        double currentSpeed = 0;
        for (int i = 0; i < gearAlphas.length; i++) {
            for (int j = 0; j < gearAlphas[i].length; j++) {
                if (gearAlphas[i][j] != null) {
                    currentSpeed = 0;
                    if (i == 0) {
                        currentSpeed = outputRPM * gearRatios[i][j];
                        gearAlphas[i][j].setStartTime((long) (System.currentTimeMillis() -
                                                              (gearAlphas[i][j].value() *
                                                               currentSpeed)));
                    } else {
                        currentSpeed = inputRPM * gearRatios[i][j];
                    }
                    gearAlphas[i][j].setIncreasingAlphaDuration((long) currentSpeed);
                }
            }
        }
        oldGearPosition = gearPosition;

        // update labels
        String rpm = "Not Connected";
        inputRPMLabel.setText("Input RPM: 4,000");
        if (currentRatio != 0) {
            rpm = String.format("%,.0f", 4000 / currentRatio);
        }
        outputRPMLabel.setText("Output RPM: " + rpm);
    }

    // shift down one gear
    public void downshift() {
        if (!shiftAlpha.finished()) {
            return;
        }
        if (gearPosition == 0) {
            return;
        }
        gearPosition--;
        shiftDescLabel.setText(shiftDescr[gearPosition][0]);
        shift();
    }

    // shift up one gear
    public void upshift() {
        if (!shiftAlpha.finished()) {
            return;
        }
        if (gearPosition == 6) {
            return;
        }
        gearPosition++;
        shiftDescLabel.setText(shiftDescr[gearPosition][1]);
        shift();
    }

    // set shift animation input RPM to fast or slow
    public void animateSpeed(ItemEvent event) {
        if (event.getStateChange() == ItemEvent.SELECTED) {
            inputRPM = 2500;
        } else {
            inputRPM = 5000;
        }
        inputAlpha.setIncreasingAlphaDuration(inputRPM);
        shift();
    }

    // turn shaft/gear rotation on/off
    public void animateShafts(ItemEvent event) {
        this.animateShafts(event.getStateChange() == ItemEvent.SELECTED);
        shift();
    }

    // turn shaft/gear rotation on/off
    public void animateShafts(boolean flag) {
        if (flag) {
            inputAlpha.resume();
            outputAlpha.resume();
            for (int i = 0; i < gearAlphas.length; i++) {
                for (int j = 0; j < gearAlphas[0].length; j++) {
                    if (gearAlphas[i][j] != null) {
                        gearAlphas[i][j].resume();
                    }
                }
            }
        } else {
            inputAlpha.pause();
            outputAlpha.pause();
            for (int i = 0; i < gearAlphas.length; i++) {
                for (int j = 0; j < gearAlphas[i].length; j++) {
                    if (gearAlphas[i][j] != null) {
                        gearAlphas[i][j].pause();
                    }
                }
            }

        }
    }

    public static void main(String[] args) {
        JMainFrame mf = new JMainFrame(new GearBox(), 640, 480);
        mf.setTitle("Motorcycle Transmission Applet");
    }
}
