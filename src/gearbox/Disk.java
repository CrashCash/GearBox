package gearbox;

import javax.media.j3d.*;
import javax.vecmath.*;

// create a disk for shaft endcaps.
public class Disk extends Shape3D {
    public Disk(double z, int normal, double radius, int segments, double arc, double offset,
                Appearance look) {
        super();
        double tempAngle;
        double c, s;
        double segmentAngle = arc / segments;
        int vertexCount = segments + 2;
        TriangleFanArray face = new TriangleFanArray(vertexCount,
                GeometryArray.NORMALS | GeometryArray.COORDINATES,
                new int[]{vertexCount});
        Vector3d normalVector = new Vector3d(0.0, 0.0, normal);
        Point3d coordinate = new Point3d(0.0, 0.0, z);
        face.setCoordinate(0, coordinate);
        face.setNormal(0, new Vector3f(normalVector));

        // invert geometry depending on how normal points
        int i = 0;
        if (normal == -1) {
            i = vertexCount;
        }
        // set default arc to be a full circle
        if (arc == 0) {
            arc = 2 * Math.PI;
        }
        for (int index = 0; index < segments + 1; index++) {
            i += normal;
            tempAngle = segmentAngle * index + offset;
            c = Math.cos(tempAngle);
            s = Math.sin(tempAngle);
            coordinate.set(radius * c, radius * s, z);
            face.setCoordinate(i, coordinate);
            face.setNormal(i, new Vector3f(normalVector));
        }
        this.setGeometry(face);
        this.setAppearance(look);
    }
}
