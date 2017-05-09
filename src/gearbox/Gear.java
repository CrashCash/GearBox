package gearbox;

import javax.media.j3d.*;
import javax.vecmath.*;

// create a realistic but lightweight gear
public class Gear extends TransformGroup {
    // shift dogs flags.
    public static enum Dogs {
        NONE,
        FRONT,
        REAR,
        BOTH;
    }

    int toothCount;
    Dogs dogsFlag;
    double gearStartAngle;
    double gearThickness;
    double toothTipThickness;
    double toothTopCenterAngle;
    double valleyCenterAngle;
    double circularPitchAngle;
    double toothValleyAngleIncrement;
    double circularToothFlatAngle;
    double circularToothEdgeAngle;
    double toothTopAngleIncrement;
    double toothDeclineAngleIncrement;
    double rootRadius;
    double outsideRadius;
    Vector3d frontNormal;
    Vector3d rearNormal;
    Appearance gearLook;
    Shape3D subShape;

    public Gear(int toothCount, double pitchCircleRadius, double shaftRadius, double addendum,
                double dedendum, double gearThickness, double toothTipThickness,
                double toothToValleyAngleRatio, Appearance gearLook, Dogs dogsFlag) {
        super();

        this.toothCount = toothCount;
        this.dogsFlag = dogsFlag;
        this.gearStartAngle = 0.0;
        this.toothTopCenterAngle = 0.0;
        this.valleyCenterAngle = 0.0;
        this.circularPitchAngle = 0.0;
        this.toothValleyAngleIncrement = 0.0;
        this.frontNormal = new Vector3d(0.0, 0.0, -1.0);
        this.rearNormal = new Vector3d(0.0, 0.0, 1.0);
        this.gearLook = gearLook;
        this.gearThickness = gearThickness;
        this.toothTipThickness = toothTipThickness;

        if (this.toothTipThickness == 0) {
            this.toothTipThickness = gearThickness;
        }

        // the angle about Z subtended by one tooth and its associated valley
        circularPitchAngle = 2.0 * Math.PI / toothCount;

        // the angle subtended by a flat (either a tooth top or a valley between teeth
        circularToothFlatAngle = circularPitchAngle * toothToValleyAngleRatio;

        // the angle subtended by the ascending or descending portion of a tooth
        circularToothEdgeAngle = circularPitchAngle / 2.0 - circularToothFlatAngle;

        // increment angles
        toothTopAngleIncrement = circularToothEdgeAngle;
        toothDeclineAngleIncrement = toothTopAngleIncrement + circularToothFlatAngle;
        toothValleyAngleIncrement = toothDeclineAngleIncrement + circularToothEdgeAngle;

        // differential angles for offsetting to the center of tooth's top and valley
        toothTopCenterAngle = toothTopAngleIncrement + circularToothFlatAngle / 2.0;
        valleyCenterAngle = toothValleyAngleIncrement + circularToothFlatAngle / 2.0;

        // gear start differential angle. All gears are constructed with the center of a tooth at
        // Z-axis angle=0
        gearStartAngle = -toothTopCenterAngle;

        // the radial distance to the root and top of the teeth, respectively
        rootRadius = pitchCircleRadius - dedendum;
        outsideRadius = pitchCircleRadius + addendum;

        // allow this object to spin. etc.
        this.setCapability(TransformGroup.ALLOW_TRANSFORM_WRITE);

        // generate the gear's body disks (the sides)
        double qtr_circle = Math.PI / 2.0;
        this.addChild(new Disk(gearThickness / 2.0, 1, rootRadius, 15, qtr_circle, 0.0, gearLook));
        this.addChild(new Disk(gearThickness / 2.0, 1, rootRadius, 15, qtr_circle, Math.PI,
                gearLook));
        this.addChild(new Disk(gearThickness / -2.0, -1, rootRadius, 15, qtr_circle, 0.0, gearLook));
        this.addChild(new Disk(gearThickness / -2.0, -1, rootRadius, 15, qtr_circle, Math.PI,
                gearLook));

        // Generate internal faces
        double z = gearThickness / 2.0;
        Vector3d normal = new Vector3d();
        Vector3d v1 = new Vector3d();
        Vector3d v2 = new Vector3d();
        double[][][] coords = {{{0.0, 0.0, -z},
                                {0.0, 0.0, z},
                                {0.0, rootRadius, z},
                                {0.0, rootRadius, -z}},
                               {{0.0, 0.0, z},
                                {0.0, 0.0, -z},
                                {rootRadius, 0.0, -z},
                                {rootRadius, 0.0, z}},
                               {{0.0, 0.0, -z},
                                {0.0, 0.0, z},
                                {0.0, -rootRadius, z},
                                {0.0, -rootRadius, -z}},
                               {{0.0, 0.0, z},
                                {0.0, 0.0, -z},
                                {-rootRadius, 0.0, -z},
                                {-rootRadius, 0.0, z}}};
        for (int i = 0; i < coords.length; i++) {
            QuadArray face = new QuadArray(4, GeometryArray.COORDINATES | GeometryArray.NORMALS);
            // Compute surface normal
            v1.sub(new Point3d(coords[i][0]), new Point3d(coords[i][1]));
            v2.sub(new Point3d(coords[i][0]), new Point3d(coords[i][2]));
            normal.cross(v1, v2);
            normal.normalize();
            int n = 0;
            for (int j = 0; j < coords[i].length; j++) {
                face.setCoordinate(n, new Point3d(coords[i][j]));
                face.setNormal(j, new Vector3f(normal));
                n++;
            }
            this.addChild(new Shape3D(face, gearLook));
        }
        // generate inside skins at tooth roots
        this.addChild(new Shaft(rootRadius, gearThickness, 5, gearLook, null, false, qtr_circle,
                Math.PI * 0.5, true));
        this.addChild(new Shaft(rootRadius, gearThickness, 5, gearLook, null, false, qtr_circle,
                Math.PI * 1.5, true));

        // generate the gear's teeth
        this.addTeeth();

        // generate shift dogs, if any
        if (dogsFlag != Dogs.NONE) {
            this.addDogs(3, (shaftRadius + rootRadius) / 2.0, rootRadius, this.gearThickness, 20.0);
        }
    }

    // construct the teeth.
    public void addTeeth() {
        // the z coordinates for the gear
        double frontZ = -0.5 * gearThickness;
        double rearZ = 0.5 * gearThickness;

        // the z coordinates for the tooth tip of the gear
        double toothTipFrontZ = -0.5 * toothTipThickness;
        double toothTipRearZ = 0.5 * toothTipThickness;

        // front and rear facing normals for the teeth faces
        Vector3d frontToothNormal = new Vector3d(0.0, 0.0, -1.0);
        Vector3d rearToothNormal = new Vector3d(0.0, 0.0, 1.0);

        // normals for teeth tops up incline, tooth top, and down incline
        Vector3d leftNormal = new Vector3d(-1.0, 0.0, 0.0);
        Vector3d rightNormal = new Vector3d(1.0, 0.0, 0.0);
        Vector3d outNormal = new Vector3d(1.0, 0.0, 0.0);
        Vector3f tempVector3f = new Vector3f();
        Point3d coordinate = new Point3d(0.0, 0.0, 0.0);
        Point3d tempCoordinate1 = new Point3d(0.0, 0.0, 0.0);
        Point3d tempCoordinate2 = new Point3d(0.0, 0.0, 0.0);
        Vector3d tempVector1 = new Vector3d(0.0, 0.0, 0.0);
        Vector3d tempVector2 = new Vector3d(0.0, 0.0, 0.0);

        int toothFaceVertexCount = 4;
        int toothFaceTotalVertexCount = toothFaceVertexCount * toothCount;
        int[] toothFaceStripCount = new int[toothCount];
        for (int i = 0; i < toothCount; i++) {
            toothFaceStripCount[i] = toothFaceVertexCount;
        }
        TriangleStripArray frontGearTeeth = new TriangleStripArray(toothFaceTotalVertexCount,
                GeometryArray.COORDINATES | GeometryArray.NORMALS, toothFaceStripCount);
        QuadArray rearGearTeeth = new QuadArray(toothCount * toothFaceVertexCount,
                GeometryArray.COORDINATES | GeometryArray.NORMALS);

        for (int i = 0; i < toothCount; i++) {
            int index = i * toothFaceVertexCount;

            // construct the gear's front facing teeth facets
            // calculate tooth angles
            double toothStartAngle = gearStartAngle + circularPitchAngle * i;
            double toothTopStartAngle = toothStartAngle + toothTopAngleIncrement;
            double toothDeclineStartAngle = toothStartAngle + toothDeclineAngleIncrement;
            double toothValleyStartAngle = toothStartAngle + toothValleyAngleIncrement;

            // calculate coordinates for tooth polygons
            double xRoot0 = rootRadius * Math.cos(toothStartAngle);
            double yRoot0 = rootRadius * Math.sin(toothStartAngle);
            double xOuter1 = outsideRadius * Math.cos(toothTopStartAngle);
            double yOuter1 = outsideRadius * Math.sin(toothTopStartAngle);
            double xOuter2 = outsideRadius * Math.cos(toothDeclineStartAngle);
            double yOuter2 = outsideRadius * Math.sin(toothDeclineStartAngle);
            double xRoot3 = rootRadius * Math.cos(toothValleyStartAngle);
            double yRoot3 = rootRadius * Math.sin(toothValleyStartAngle);

            // compute surface normal vector for lighting
            tempCoordinate1.set(xRoot0, yRoot0, frontZ);
            tempCoordinate2.set(xRoot3, yRoot3, frontZ);
            tempVector1.sub(tempCoordinate2, tempCoordinate1);
            tempCoordinate2.set(xOuter1, yOuter1, toothTipFrontZ);
            tempVector2.sub(tempCoordinate2, tempCoordinate1);
            frontToothNormal.cross(tempVector1, tempVector2);
            frontToothNormal.normalize();

            // set polygon coordinates & normals
            coordinate.set(xOuter1, yOuter1, toothTipFrontZ);
            frontGearTeeth.setCoordinate(index, coordinate);
            tempVector3f = new Vector3f(frontToothNormal);
            frontGearTeeth.setNormal(index, tempVector3f);

            coordinate.set(xRoot0, yRoot0, frontZ);
            frontGearTeeth.setCoordinate(index + 1, coordinate);
            frontGearTeeth.setNormal(index + 1, tempVector3f);

            coordinate.set(xOuter2, yOuter2, toothTipFrontZ);
            frontGearTeeth.setCoordinate(index + 2, coordinate);
            frontGearTeeth.setNormal(index + 2, tempVector3f);

            coordinate.set(xRoot3, yRoot3, frontZ);
            frontGearTeeth.setCoordinate(index + 3, coordinate);
            frontGearTeeth.setNormal(index + 3, tempVector3f);

            // construct the gear's rear facing teeth facets (using quads)
            // calculate coordinates for tooth polygons
            xRoot0 = rootRadius * Math.cos(toothStartAngle);
            yRoot0 = rootRadius * Math.sin(toothStartAngle);
            xOuter1 = outsideRadius * Math.cos(toothTopStartAngle);
            yOuter1 = outsideRadius * Math.sin(toothTopStartAngle);
            xOuter2 = outsideRadius * Math.cos(toothDeclineStartAngle);
            yOuter2 = outsideRadius * Math.sin(toothDeclineStartAngle);
            xRoot3 = rootRadius * Math.cos(toothValleyStartAngle);
            yRoot3 = rootRadius * Math.sin(toothValleyStartAngle);

            // compute surface normal vector for lighting
            tempCoordinate1.set(xRoot0, yRoot0, rearZ);
            tempCoordinate2.set(xRoot3, yRoot3, rearZ);
            tempVector1.sub(tempCoordinate2, tempCoordinate1);
            tempCoordinate2.set(xOuter1, yOuter1, toothTipRearZ);
            tempVector2.sub(tempCoordinate2, tempCoordinate1);
            rearToothNormal.cross(tempVector2, tempVector1);
            rearToothNormal.normalize();

            // set polygon coordinates & normals
            coordinate.set(xRoot0, yRoot0, rearZ);
            tempVector3f = new Vector3f(rearToothNormal);
            rearGearTeeth.setCoordinate(index, coordinate);
            rearGearTeeth.setNormal(index, tempVector3f);

            coordinate.set(xOuter1, yOuter1, toothTipRearZ);
            rearGearTeeth.setCoordinate(index + 1, coordinate);
            rearGearTeeth.setNormal(index + 1, tempVector3f);

            coordinate.set(xOuter2, yOuter2, toothTipRearZ);
            rearGearTeeth.setCoordinate(index + 2, coordinate);
            rearGearTeeth.setNormal(index + 2, tempVector3f);

            coordinate.set(xRoot3, yRoot3, rearZ);
            rearGearTeeth.setCoordinate(index + 3, coordinate);
            rearGearTeeth.setNormal(index + 3, tempVector3f);
        }
        Shape3D newShape = new Shape3D(frontGearTeeth, gearLook);
        this.addChild(newShape);
        newShape = new Shape3D(rearGearTeeth, gearLook);
        this.addChild(newShape);

        // construct the gear's top teeth faces
        int toothFacetVertexCount = 4;
        int toothFacetCount = 4;
        QuadArray topGearTeeth = new QuadArray(toothCount * toothFacetVertexCount * toothFacetCount,
                GeometryArray.COORDINATES | GeometryArray.NORMALS);
        for (int i = 0; i < toothCount; i++) {
            int index = i * toothFacetCount * toothFacetVertexCount;
            double toothStartAngle = gearStartAngle + circularPitchAngle * i;
            double toothTopStartAngle = toothStartAngle + toothTopAngleIncrement;
            double toothDeclineStartAngle = toothStartAngle + toothDeclineAngleIncrement;
            double toothValleyStartAngle = toothStartAngle + toothValleyAngleIncrement;
            double nextToothStartAngle = toothStartAngle + circularPitchAngle;

            double xRoot0 = rootRadius * Math.cos(toothStartAngle);
            double yRoot0 = rootRadius * Math.sin(toothStartAngle);
            double xOuter1 = outsideRadius * Math.cos(toothTopStartAngle);
            double yOuter1 = outsideRadius * Math.sin(toothTopStartAngle);
            double xOuter2 = outsideRadius * Math.cos(toothDeclineStartAngle);
            double yOuter2 = outsideRadius * Math.sin(toothDeclineStartAngle);
            double xRoot3 = rootRadius * Math.cos(toothValleyStartAngle);
            double yRoot3 = rootRadius * Math.sin(toothValleyStartAngle);
            double xRoot4 = rootRadius * Math.cos(nextToothStartAngle);
            double yRoot4 = rootRadius * Math.sin(nextToothStartAngle);

            // compute normal for quad 1 (1st sloping side)
            tempCoordinate1.set(xRoot0, yRoot0, frontZ);
            tempCoordinate2.set(xOuter1, yOuter1, toothTipFrontZ);
            tempVector1.sub(tempCoordinate2, tempCoordinate1);
            leftNormal.cross(frontNormal, tempVector1);
            leftNormal.normalize();

            // coordinate labelled 0 in the quad
            coordinate.set(xRoot0, yRoot0, rearZ);
            tempVector3f = new Vector3f(leftNormal);
            topGearTeeth.setCoordinate(index, coordinate);
            topGearTeeth.setNormal(index, tempVector3f);

            // coordinate labelled 1 in the quad
            coordinate.set(tempCoordinate1);
            topGearTeeth.setCoordinate(index + 1, coordinate);
            topGearTeeth.setNormal(index + 1, tempVector3f);

            // coordinate labelled 2 in the quad
            topGearTeeth.setCoordinate(index + 2, tempCoordinate2);
            topGearTeeth.setNormal(index + 2, tempVector3f);
            topGearTeeth.setCoordinate(index + 5, tempCoordinate2);

            // coordinate labelled 3 in the quad
            coordinate.set(xOuter1, yOuter1, toothTipRearZ);
            topGearTeeth.setCoordinate(index + 3, coordinate);
            topGearTeeth.setNormal(index + 3, tempVector3f);
            topGearTeeth.setCoordinate(index + 4, coordinate);

            // compute normal for quad 2 (outer tip)
            tempCoordinate1.set(xOuter1, yOuter1, toothTipFrontZ);
            tempCoordinate2.set(xOuter2, yOuter2, toothTipFrontZ);
            tempVector1.sub(tempCoordinate2, tempCoordinate1);
            outNormal.cross(frontNormal, tempVector1);
            outNormal.normalize();
            tempVector3f = new Vector3f(outNormal);
            topGearTeeth.setNormal(index + 4, tempVector3f);
            topGearTeeth.setNormal(index + 5, tempVector3f);

            // Coordinate labelled 4 in the quad
            topGearTeeth.setCoordinate(index + 6, tempCoordinate2);
            topGearTeeth.setNormal(index + 6, tempVector3f);
            topGearTeeth.setCoordinate(index + 9, tempCoordinate2);

            // Coordinate labeled 5 in the quad
            coordinate.set(xOuter2, yOuter2, toothTipRearZ);
            topGearTeeth.setCoordinate(index + 7, coordinate);
            topGearTeeth.setNormal(index + 7, tempVector3f);
            topGearTeeth.setCoordinate(index + 8, coordinate);

            // Compute normal for quad 3 (2nd sloping side)
            tempCoordinate1.set(xOuter2, yOuter2, toothTipFrontZ);
            tempCoordinate2.set(xRoot3, yRoot3, frontZ);
            tempVector1.sub(tempCoordinate2, tempCoordinate1);
            rightNormal.cross(frontNormal, tempVector1);
            rightNormal.normalize();
            tempVector3f = new Vector3f(rightNormal);
            topGearTeeth.setNormal(index + 8, tempVector3f);
            topGearTeeth.setNormal(index + 9, tempVector3f);

            // Coordinate labelled 7 in the quad
            topGearTeeth.setCoordinate(index + 10, tempCoordinate2);
            topGearTeeth.setNormal(index + 10, tempVector3f);
            topGearTeeth.setCoordinate(index + 13, tempCoordinate2);

            // Coordinate labelled 6 in the quad
            coordinate.set(xRoot3, yRoot3, rearZ);
            topGearTeeth.setCoordinate(index + 11, coordinate);
            topGearTeeth.setNormal(index + 11, tempVector3f);
            topGearTeeth.setCoordinate(index + 12, coordinate);

            // Compute normal for quad 4 (root between teeth)
            tempCoordinate1.set(xRoot3, yRoot3, frontZ);
            tempCoordinate2.set(xRoot4, yRoot4, frontZ);
            tempVector1.sub(tempCoordinate2, tempCoordinate1);
            outNormal.cross(frontNormal, tempVector1);
            outNormal.normalize();
            tempVector3f = new Vector3f(outNormal);
            topGearTeeth.setNormal(index + 12, tempVector3f);
            topGearTeeth.setNormal(index + 13, tempVector3f);

            // Coordinate labeled 9 in the quad
            topGearTeeth.setCoordinate(index + 14, tempCoordinate2);
            topGearTeeth.setNormal(index + 14, tempVector3f);

            // Coordinate labeled 8 in the quad
            coordinate.set(xRoot4, yRoot4, rearZ);
            topGearTeeth.setCoordinate(index + 15, coordinate);
            topGearTeeth.setNormal(index + 15, tempVector3f);

            toothTopStartAngle = nextToothStartAngle + toothTopAngleIncrement;
        }
        newShape = new Shape3D(topGearTeeth, gearLook);
        newShape.setCapability(Shape3D.ALLOW_APPEARANCE_WRITE);
        this.addChild(newShape);
        subShape = newShape;
    }

    // add shift dogs (front, rear, or both)
    public void addDogs(int dogCount, double innerRadius, double outerRadius, double width,
                        double arc) {
        Vector3f tempVector3f = new Vector3f();
        Point3d coordinate = new Point3d(0.0, 0.0, 0.0);
        int faces = 16 * dogCount;
        double frontZ = 0.0;
        double rearZ = 0.0;
        if (dogsFlag != Dogs.FRONT) {
            frontZ = -width;
            faces += 4 * dogCount;
        }
        if (dogsFlag != Dogs.REAR) {
            rearZ = width;
            faces += 4 * dogCount;
        }

        // generate all dogs
        double angleInc = 2.0 * Math.PI / dogCount;
        double arcRadians = Math.toRadians(arc);
        int index = 0;
        double angle0 = 0;
        QuadArray dogFaces = new QuadArray(faces, GeometryArray.COORDINATES | GeometryArray.NORMALS);
        for (int i = 0; i < dogCount; i++) {
            // compute raw coordinates
            double angle1 = angle0 + arcRadians;
            double xDirection0 = Math.cos(angle0);
            double yDirection0 = Math.sin(angle0);
            double xDirection1 = Math.cos(angle1);
            double yDirection1 = Math.sin(angle1);
            double xInner0 = innerRadius * xDirection0;
            double yInner0 = innerRadius * yDirection0;
            double xInner1 = innerRadius * xDirection1;
            double yInner1 = innerRadius * yDirection1;
            double xOuter0 = outerRadius * xDirection0;
            double yOuter0 = outerRadius * yDirection0;
            double xOuter1 = outerRadius * xDirection1;
            double yOuter1 = outerRadius * yDirection1;

            // generate front face
            if (frontZ != 0) {
                tempVector3f = new Vector3f(frontNormal);
                coordinate.set(xInner0, yInner0, frontZ);
                dogFaces.setCoordinate(index, coordinate);
                dogFaces.setNormal(index, tempVector3f);
                index++;
                coordinate.set(xInner1, yInner1, frontZ);
                dogFaces.setCoordinate(index, coordinate);
                dogFaces.setNormal(index, tempVector3f);
                index++;
                coordinate.set(xOuter1, yOuter1, frontZ);
                dogFaces.setCoordinate(index, coordinate);
                dogFaces.setNormal(index, tempVector3f);
                index++;
                coordinate.set(xOuter0, yOuter0, frontZ);
                dogFaces.setCoordinate(index, coordinate);
                dogFaces.setNormal(index, tempVector3f);
                index++;
            }
            // Generate rear face
            if (rearZ != 0) {
                tempVector3f = new Vector3f(rearNormal);
                coordinate.set(xInner0, yInner0, rearZ);
                dogFaces.setCoordinate(index, coordinate);
                dogFaces.setNormal(index, tempVector3f);
                index++;
                coordinate.set(xOuter0, yOuter0, rearZ);
                dogFaces.setCoordinate(index, coordinate);
                dogFaces.setNormal(index, tempVector3f);
                index++;
                coordinate.set(xOuter1, yOuter1, rearZ);
                dogFaces.setCoordinate(index, coordinate);
                dogFaces.setNormal(index, tempVector3f);
                index++;
                coordinate.set(xInner1, yInner1, rearZ);
                dogFaces.setCoordinate(index, coordinate);
                dogFaces.setNormal(index, tempVector3f);
                index++;
            }

            // generate side faces
            double[][][] coords = {{{xOuter0, yOuter0, frontZ},
                                    {xOuter1, yOuter1, frontZ},
                                    {xOuter1, yOuter1, rearZ},
                                    {xOuter0, yOuter0, rearZ}},
                                   {{xInner0, yInner0, rearZ},
                                    {xInner1, yInner1, rearZ},
                                    {xInner1, yInner1, frontZ},
                                    {xInner0, yInner0, frontZ}},
                                   {{xOuter1, yOuter1, frontZ},
                                    {xInner1, yInner1, frontZ},
                                    {xInner1, yInner1, rearZ},
                                    {xOuter1, yOuter1, rearZ}},
                                   {{xOuter0, yOuter0, rearZ},
                                    {xInner0, yInner0, rearZ},
                                    {xInner0, yInner0, frontZ},
                                    {xOuter0, yOuter0, frontZ}}};
            Vector3d tempVector1 = new Vector3d();
            Vector3d tempVector2 = new Vector3d();
            Vector3d normal = new Vector3d();
            for (int j = 0; j < coords.length; j++) {
                // compute surface normal for lighting
                Point3d tempCoordinate1 = new Point3d(coords[j][0]);
                Point3d tempCoordinate2 = new Point3d(coords[j][1]);
                Point3d tempCoordinate3 = new Point3d(coords[j][2]);
                tempVector1.sub(tempCoordinate1, tempCoordinate2);
                tempVector2.sub(tempCoordinate1, tempCoordinate3);
                normal.cross(tempVector1, tempVector2);
                normal.normalize();
                for (int k = 0; k < coords[j].length; k++) {
                    coordinate.set(coords[j][k]);
                    dogFaces.setCoordinate(index, coordinate);
                    dogFaces.setNormal(index, new Vector3f(normal));
                    index++;
                }
            }
            angle0 += angleInc;
        }
        Shape3D newShape = new Shape3D(dogFaces, gearLook);
        this.addChild(newShape);
    }

    public double getCircularPitchAngle() {
        return circularPitchAngle;
    }

    public Shape3D getSubShape() {
        return subShape;
    }

    public void setSubShape(Shape3D sub_shape) {
        subShape = sub_shape;
    }
}
