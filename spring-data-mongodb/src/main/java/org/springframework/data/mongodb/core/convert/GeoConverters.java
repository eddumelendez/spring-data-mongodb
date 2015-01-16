/*
 * Copyright 2014-2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.data.mongodb.core.convert;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.bson.BSONObject;
import org.springframework.beans.DirectFieldAccessor;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.convert.WritingConverter;
import org.springframework.data.geo.Box;
import org.springframework.data.geo.Circle;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.Metrics;
import org.springframework.data.geo.Point;
import org.springframework.data.geo.Polygon;
import org.springframework.data.mongodb.core.geo.GeoJson;
import org.springframework.data.mongodb.core.geo.Sphere;
import org.springframework.data.mongodb.core.query.GeoCommand;
import org.springframework.data.util.ClassTypeInformation;
import org.springframework.data.util.TypeInformation;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.ObjectUtils;

import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;

/**
 * Wrapper class to contain useful geo structure converters for the usage with Mongo.
 * 
 * @author Thomas Darimont
 * @author Oliver Gierke
 * @author Christoph Strobl
 * @since 1.5
 */
abstract class GeoConverters {

	/**
	 * Private constructor to prevent instantiation.
	 */
	private GeoConverters() {}

	/**
	 * Returns the geo converters to be registered.
	 * 
	 * @return
	 */
	public static Collection<? extends Object> getConvertersToRegister() {
		return Arrays.asList( //
				BoxToDbObjectConverter.INSTANCE //
				, PolygonToDbObjectConverter.INSTANCE //
				, CircleToDbObjectConverter.INSTANCE //
				, SphereToDbObjectConverter.INSTANCE //
				, DbObjectToBoxConverter.INSTANCE //
				, DbObjectToPolygonConverter.INSTANCE //
				, DbObjectToCircleConverter.INSTANCE //
				, DbObjectToSphereConverter.INSTANCE //
				, DbObjectToPointConverter.INSTANCE //
				, PointToDbObjectConverter.INSTANCE //
				, GeoCommandToDbObjectConverter.INSTANCE //
				, GeoJsonToDbObjectConverter.INSTANCE //
				, DbObjectToGeoJsonConverter.INSTANCE);
	}

	/**
	 * Converts a {@link List} of {@link Double}s into a {@link Point}.
	 * 
	 * @author Thomas Darimont
	 * @since 1.5
	 */
	@ReadingConverter
	public static enum DbObjectToPointConverter implements Converter<DBObject, Point> {

		INSTANCE;

		/*
		 * (non-Javadoc)
		 * @see org.springframework.core.convert.converter.Converter#convert(java.lang.Object)
		 */
		@Override
		public Point convert(DBObject source) {

			if (source == null) {
				return null;
			}

			if (source instanceof BasicDBList) {
				return new Point((Double) ((BasicDBList) source).get(0), (Double) ((BasicDBList) source).get(1));
			}

			return ObjectUtils.nullSafeEquals(source.get("type"), "Point") ? convertGeoJson(source)
					: convertLegacyCoordinates(source);
		}

		private Point convertGeoJson(DBObject geoJson) {
			return (Point) DbObjectToGeoJsonConverter.INSTANCE.convert(geoJson).getGeometry();
		}

		private Point convertLegacyCoordinates(BSONObject coodrinates) {

			Assert.isTrue(coodrinates.keySet().size() == 2, "Source must contain 2 elements");
			return new Point((Double) coodrinates.get("x"), (Double) coodrinates.get("y"));
		}
	}

	/**
	 * Converts a {@link Point} into a {@link List} of {@link Double}s.
	 * 
	 * @author Thomas Darimont
	 * @since 1.5
	 */
	public static enum PointToDbObjectConverter implements Converter<Point, DBObject> {

		INSTANCE;

		/*
		 * (non-Javadoc)
		 * @see org.springframework.core.convert.converter.Converter#convert(java.lang.Object)
		 */
		@Override
		public DBObject convert(Point source) {
			return source == null ? null : new BasicDBObject("x", source.getX()).append("y", source.getY());
		}
	}

	/**
	 * Converts a {@link Box} into a {@link BasicDBList}.
	 * 
	 * @author Thomas Darimont
	 * @since 1.5
	 */
	@WritingConverter
	public static enum BoxToDbObjectConverter implements Converter<Box, DBObject> {

		INSTANCE;

		/*
		 * (non-Javadoc)
		 * @see org.springframework.core.convert.converter.Converter#convert(java.lang.Object)
		 */
		@Override
		public DBObject convert(Box source) {

			if (source == null) {
				return null;
			}

			BasicDBObject result = new BasicDBObject();
			result.put("first", PointToDbObjectConverter.INSTANCE.convert(source.getFirst()));
			result.put("second", PointToDbObjectConverter.INSTANCE.convert(source.getSecond()));
			return result;
		}
	}

	/**
	 * Converts a {@link BasicDBList} into a {@link Box}.
	 * 
	 * @author Thomas Darimont
	 * @since 1.5
	 */
	@ReadingConverter
	public static enum DbObjectToBoxConverter implements Converter<DBObject, Box> {

		INSTANCE;

		/*
		 * (non-Javadoc)
		 * @see org.springframework.core.convert.converter.Converter#convert(java.lang.Object)
		 */
		@Override
		public Box convert(DBObject source) {

			if (source == null) {
				return null;
			}

			return ObjectUtils.nullSafeEquals(source.get("type"), "Polygon") ? convertGeoJson(source)
					: convertLegacyCoordinates(source);

		}

		private Box convertLegacyCoordinates(DBObject coodrinates) {

			Point first = DbObjectToPointConverter.INSTANCE.convert((DBObject) coodrinates.get("first"));
			Point second = DbObjectToPointConverter.INSTANCE.convert((DBObject) coodrinates.get("second"));

			return new Box(first, second);
		}

		@SuppressWarnings("unchecked")
		private Box convertGeoJson(DBObject geoJson) {

			BasicDBList coordinates = (BasicDBList) geoJson.get("coordinates");
			List<List<Double>> points = (List<List<Double>>) coordinates.get(0);
			List<Point> newPoints = new ArrayList<Point>(points.size());

			for (List<Double> element : points) {

				Assert.notNull(element, "Point elements of polygon must not be null!");
				newPoints.add(new Point(element.get(0), element.get(1)));
			}

			return new Box(newPoints.get(0), newPoints.get(2));
		}
	}

	/**
	 * Converts a {@link Circle} into a {@link BasicDBList}.
	 * 
	 * @author Thomas Darimont
	 * @since 1.5
	 */
	public static enum CircleToDbObjectConverter implements Converter<Circle, DBObject> {

		INSTANCE;

		/*
		 * (non-Javadoc)
		 * @see org.springframework.core.convert.converter.Converter#convert(java.lang.Object)
		 */
		@Override
		public DBObject convert(Circle source) {

			if (source == null) {
				return null;
			}

			DBObject result = new BasicDBObject();
			result.put("center", PointToDbObjectConverter.INSTANCE.convert(source.getCenter()));
			result.put("radius", source.getRadius().getNormalizedValue());
			result.put("metric", source.getRadius().getMetric().toString());
			return result;
		}
	}

	/**
	 * Converts a {@link DBObject} into a {@link Circle}.
	 * 
	 * @author Thomas Darimont
	 * @since 1.5
	 */
	@ReadingConverter
	public static enum DbObjectToCircleConverter implements Converter<DBObject, Circle> {

		INSTANCE;

		/*
		 * (non-Javadoc)
		 * @see org.springframework.core.convert.converter.Converter#convert(java.lang.Object)
		 */
		@Override
		public Circle convert(DBObject source) {

			if (source == null) {
				return null;
			}

			DBObject center = (DBObject) source.get("center");
			Double radius = (Double) source.get("radius");

			Distance distance = new Distance(radius);

			if (source.containsField("metric")) {

				String metricString = (String) source.get("metric");
				Assert.notNull(metricString, "Metric must not be null!");

				distance = distance.in(Metrics.valueOf(metricString));
			}

			Assert.notNull(center, "Center must not be null!");
			Assert.notNull(radius, "Radius must not be null!");

			return new Circle(DbObjectToPointConverter.INSTANCE.convert(center), distance);
		}
	}

	/**
	 * Converts a {@link Sphere} into a {@link BasicDBList}.
	 * 
	 * @author Thomas Darimont
	 * @since 1.5
	 */
	public static enum SphereToDbObjectConverter implements Converter<Sphere, DBObject> {

		INSTANCE;

		/*
		 * (non-Javadoc)
		 * @see org.springframework.core.convert.converter.Converter#convert(java.lang.Object)
		 */
		@Override
		public DBObject convert(Sphere source) {

			if (source == null) {
				return null;
			}

			DBObject result = new BasicDBObject();
			result.put("center", PointToDbObjectConverter.INSTANCE.convert(source.getCenter()));
			result.put("radius", source.getRadius().getNormalizedValue());
			result.put("metric", source.getRadius().getMetric().toString());
			return result;
		}
	}

	/**
	 * Converts a {@link BasicDBList} into a {@link Sphere}.
	 * 
	 * @author Thomas Darimont
	 * @since 1.5
	 */
	@ReadingConverter
	public static enum DbObjectToSphereConverter implements Converter<DBObject, Sphere> {

		INSTANCE;

		/*
		 * (non-Javadoc)
		 * @see org.springframework.core.convert.converter.Converter#convert(java.lang.Object)
		 */
		@Override
		public Sphere convert(DBObject source) {

			if (source == null) {
				return null;
			}

			DBObject center = (DBObject) source.get("center");
			Double radius = (Double) source.get("radius");

			Distance distance = new Distance(radius);

			if (source.containsField("metric")) {

				String metricString = (String) source.get("metric");
				Assert.notNull(metricString, "Metric must not be null!");

				distance = distance.in(Metrics.valueOf(metricString));
			}

			Assert.notNull(center, "Center must not be null!");
			Assert.notNull(radius, "Radius must not be null!");

			return new Sphere(DbObjectToPointConverter.INSTANCE.convert(center), distance);
		}
	}

	/**
	 * Converts a {@link Polygon} into a {@link BasicDBList}.
	 * 
	 * @author Thomas Darimont
	 * @since 1.5
	 */
	public static enum PolygonToDbObjectConverter implements Converter<Polygon, DBObject> {

		INSTANCE;

		/*
		 * (non-Javadoc)
		 * @see org.springframework.core.convert.converter.Converter#convert(java.lang.Object)
		 */
		@Override
		public DBObject convert(Polygon source) {

			if (source == null) {
				return null;
			}

			List<Point> points = source.getPoints();
			List<DBObject> pointTuples = new ArrayList<DBObject>(points.size());

			for (Point point : points) {
				pointTuples.add(PointToDbObjectConverter.INSTANCE.convert(point));
			}

			DBObject result = new BasicDBObject();
			result.put("points", pointTuples);
			return result;
		}
	}

	/**
	 * Converts a {@link BasicDBList} into a {@link Polygon}.
	 * 
	 * @author Thomas Darimont
	 * @since 1.5
	 */
	@ReadingConverter
	public static enum DbObjectToPolygonConverter implements Converter<DBObject, Polygon> {

		INSTANCE;

		/*
		 * (non-Javadoc)
		 * @see org.springframework.core.convert.converter.Converter#convert(java.lang.Object)
		 */
		@Override
		@SuppressWarnings({ "unchecked" })
		public Polygon convert(DBObject source) {

			if (source == null) {
				return null;
			}

			if (ObjectUtils.nullSafeEquals(source.get("type"), "Polygon")) {
				return (Polygon) DbObjectToGeoJsonConverter.INSTANCE.convert(source).getGeometry();
			}

			List<DBObject> points = (List<DBObject>) source.get("points");
			List<Point> newPoints = new ArrayList<Point>(points.size());

			for (DBObject element : points) {

				Assert.notNull(element, "Point elements of polygon must not be null!");
				newPoints.add(DbObjectToPointConverter.INSTANCE.convert(element));
			}

			return new Polygon(newPoints);
		}

	}

	/**
	 * Converts a {@link Sphere} into a {@link BasicDBList}.
	 * 
	 * @author Thomas Darimont
	 * @since 1.5
	 */
	public static enum GeoCommandToDbObjectConverter implements Converter<GeoCommand, DBObject> {

		INSTANCE;

		/*
		 * (non-Javadoc)
		 * @see org.springframework.core.convert.converter.Converter#convert(java.lang.Object)
		 */
		@Override
		public DBObject convert(GeoCommand source) {

			if (source == null) {
				return null;
			}

			BasicDBList argument = new BasicDBList();

			Object shape = source.getShape();

			if (shape instanceof Box) {

				argument.add(toList(((Box) shape).getFirst()));
				argument.add(toList(((Box) shape).getSecond()));

			} else if (shape instanceof Circle) {

				argument.add(toList(((Circle) shape).getCenter()));
				argument.add(((Circle) shape).getRadius().getNormalizedValue());

			} else if (shape instanceof Circle) {

				argument.add(toList(((Circle) shape).getCenter()));
				argument.add(((Circle) shape).getRadius());

			} else if (shape instanceof Polygon) {

				for (Point point : ((Polygon) shape).getPoints()) {
					argument.add(toList(point));
				}

			} else if (shape instanceof Sphere) {

				argument.add(toList(((Sphere) shape).getCenter()));
				argument.add(((Sphere) shape).getRadius().getNormalizedValue());
			}

			return new BasicDBObject(source.getCommand(), argument);
		}
	}

	/**
	 * Converts a {@link GeoJson} into a {@link DBObject}.
	 * 
	 * @author Christoph Strobl
	 * @since 1.7
	 */
	public static enum GeoJsonToDbObjectConverter implements Converter<GeoJson<?>, DBObject> {

		INSTANCE;

		/*
		 * (non-Javadoc)
		 * @see org.springframework.core.convert.converter.Converter#convert(java.lang.Object)
		 */
		@Override
		public DBObject convert(GeoJson<?> source) {

			if (source == null) {
				return null;
			}

			Object geometry = source.getGeometry();

			DBObject geometryDbo = new BasicDBObject();

			if (geometry instanceof Point) {

				geometryDbo.put("type", "Point");
				geometryDbo.put("coordinates", toList((Point) geometry));
			}

			else if (geometry instanceof double[]) {

				double[] values = (double[]) geometry;
				if (values.length != 2) {
					throw new IllegalArgumentException("Point coordinates need to have x and y value.");
				}

				geometryDbo.put("type", "Point");
				geometryDbo.put("coordinates", toList(new Point(values[0], values[1])));
			}

			else if (geometry instanceof Box) {

				geometryDbo.put("type", "Polygon");

				Point p1 = ((Box) geometry).getFirst();
				Point p3 = ((Box) geometry).getSecond();

				Point p2 = new Point(p1.getX(), p3.getY());
				Point p4 = new Point(p3.getX(), p1.getY());

				Point p5 = ((Box) geometry).getFirst();

				geometryDbo.put("coordinates", toCoordinates(p1, p2, p3, p4, p5));
			}

			else if (geometry instanceof Polygon) {

				geometryDbo.put("type", "Polygon");

				List<Point> points = new ArrayList<Point>(((Polygon) geometry).getPoints());

				Point first = points.get(0);
				Point last = points.get(points.size() - 1);

				if (!first.equals(last)) {
					points.add(first);
				}

				geometryDbo.put("coordinates", toCoordinates(points));
			}

			else {

				ClassTypeInformation<?> info = ClassTypeInformation.from(geometry.getClass());
				TypeInformation<?> typeProperty = info.getProperty("type");

				if (typeProperty != null && ClassUtils.isAssignable(String.class, typeProperty.getType())) {

					DirectFieldAccessor dfa = new DirectFieldAccessor(geometry);

					BasicDBObject dbo = new BasicDBObject("type", dfa.getPropertyValue("type"));
					dbo.put("coordinates", dfa.getPropertyValue("coordinates"));
					return dbo;

				}

				throw new IllegalArgumentException(String.format("Unknown GeoJson type %s!", geometry.getClass()));
			}

			return geometryDbo;
		}
	}

	static enum DbObjectToGeoJsonConverter implements Converter<DBObject, GeoJson<?>> {
		INSTANCE;

		@Override
		public GeoJson<?> convert(DBObject source) {

			if (source == null) {
				return null;
			}

			if (!source.containsField("type")) {
				throw new IllegalArgumentException("GeoJson needs to specify type");
			}

			String type = source.get("type").toString();

			if ("Point".equals(type)) {
				List<Double> dbl = (List<Double>) source.get("coordinates");
				return GeoJson.point(dbl.get(0), dbl.get(1));
			}

			if ("Polygon".equals(type)) {

				BasicDBList dbl = (BasicDBList) source.get("coordinates");
				List<DBObject> points = (List<DBObject>) dbl.get(0);
				List<Point> newPoints = new ArrayList<Point>(points.size());

				for (DBObject element : points) {

					Assert.notNull(element, "Point elements of polygon must not be null!");
					newPoints.add(DbObjectToPointConverter.INSTANCE.convert(element));
				}

				return GeoJson.polygon(new Polygon(newPoints));
			}

			throw new IllegalArgumentException(String.format("Unknown GeoJson type %s!", type));
		}

	}

	static List<Double> toList(Point point) {
		return Arrays.asList(point.getX(), point.getY());
	}

	static BSONObject toCoordinates(Point... points) {
		return toCoordinates(Arrays.asList(points));
	}

	static BSONObject toCoordinates(List<Point> points) {

		BasicDBList pointList = new BasicDBList();
		for (Point point : points) {
			pointList.add(toList(point));
		}

		BasicDBList coordinates = new BasicDBList();
		coordinates.add(pointList);

		return coordinates;
	}
}
