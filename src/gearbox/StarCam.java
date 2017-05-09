package gearbox;

import javax.media.j3d.*;
import javax.vecmath.*;

// create star indexing cam on end of shift cam
public class StarCam extends TransformGroup {
    Shape3D skin;

    public StarCam(double radius, int segments, Appearance look, double offset, double width) {
        super();
        // front face
        TriangleFanArray face1 = new TriangleFanArray(segments + 2,
                GeometryArray.NORMALS |
                GeometryArray.COORDINATES, new int[]{
                    segments + 2});
        // back face
        TriangleFanArray face2 = new TriangleFanArray(segments + 2,
                GeometryArray.NORMALS |
                GeometryArray.COORDINATES,
                new int[]{segments + 2});
        // skin between faces on cam surface
        TriangleStripArray face3 = new TriangleStripArray(segments * 2 + 2,
                GeometryArray.NORMALS |
                GeometryArray.COORDINATES,
                new int[]{segments * 2 + 2});

        double segmentAngle = 2.0 * Math.PI / segments;
        Vector3f normal1 = new Vector3f(0.0F, 0.0F, 1.0F);
        Vector3f normal2 = new Vector3f(0.0F, 0.0F, -1.0F);

        // Set center of triangle fans
        Point3d coordinate = new Point3d(0.0, 0.0, offset + width);
        face1.setCoordinate(0, coordinate);
        face1.setNormal(0, normal1);
        coordinate = new Point3d(0.0, 0.0, offset);
        face2.setCoordinate(0, coordinate);
        face2.setNormal(0, normal2);
        for (int index = 0; index < segments + 1; index++) {
            double tempAngle = segmentAngle * index;
            double c = Math.cos(tempAngle);
            double s = Math.sin(tempAngle);
            double r = radius - profile(tempAngle);

            coordinate.set(r * c, r * s, offset + width);
            int i = index + 1;
            face1.setCoordinate(i, coordinate);
            face1.setNormal(i, normal1);
            face3.setCoordinate(index * 2, coordinate);
            coordinate.set(r * c, r * s, offset);
            i = segments + 1 - index;
            face2.setCoordinate(i, coordinate);
            face2.setNormal(i, normal2);
            face3.setCoordinate(index * 2 + 1, coordinate);
            Vector3f normal3 = new Vector3f((float) c, (float) s, 0.0F);
            face3.setNormal(index * 2, normal3);
            face3.setNormal(index * 2 + 1, normal3);
        }
        this.addChild(new Shape3D(face1, look));
        this.addChild(new Shape3D(face2, look));
        skin = new Shape3D(face3, look);
        skin.setCapability(Shape3D.ALLOW_GEOMETRY_READ);
        this.addChild(skin);
    }

    // compute cam profile as a function of angle
    public static double profile(double angle) {
        double f = Math.abs(Math.sin(angle * 3)) / 5;
        double x = Math.toDegrees(angle);
        // This is the little "nip" for neutral
        if (x >= 170 && x <= 190) {
            f = Math.abs(Math.sin(angle * 4.5)) / 6;
        }
        return f;
    }
}
