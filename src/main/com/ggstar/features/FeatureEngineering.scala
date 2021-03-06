package com.ggstar.features

import org.apache.spark.ml.{Pipeline, PipelineModel, PipelineStage}
import org.apache.spark.ml.feature._
import org.apache.spark.ml.linalg.{DenseVector, Vectors}
import org.apache.spark.sql.DataFrame

class FeatureEngineering {

  //transform array(user_embedding, item_embedding) to vector for following vectorAssembler
  def transferArray2Vector(samples:DataFrame):DataFrame = {
    import samples.sparkSession.implicits._

    samples
      .map(row => {
        (row.getAs[Int]("user_id"),
          row.getAs[Int]("item_id"),
          row.getAs[Int]("category_id"),
          row.getAs[String]("content_type"),
          row.getAs[String]("timestamp"),
          row.getAs[Long]("user_item_click"),
          row.getAs[Double]("user_item_imp"),
          row.getAs[Double]("item_ctr"),
          row.getAs[Int]("is_new_user"),
          Vectors.dense(row.getAs[Seq[Double]]("user_embedding").toArray),
          Vectors.dense(row.getAs[Seq[Double]]("item_embedding").toArray),
          row.getAs[Int]("label")
        )
      }).toDF(
      "user_id",
      "item_id",
      "category_id",
      "content_type",
      "timestamp",
      "user_item_click",
      "user_item_imp",
      "item_ctr",
      "is_new_user",
      "user_embedding",
      "item_embedding",
      "label")
  }

  //calculate inner product between user embedding and item embedding
  def calculateEmbeddingInnerProduct(samples:DataFrame): DataFrame ={
    import samples.sparkSession.implicits._

    samples.map(row => {
      val user_embedding = row.getAs[DenseVector]("user_embedding")
      val item_embedding = row.getAs[DenseVector]("item_embedding")
      var asquare = 0.0
      var bsquare = 0.0
      var abmul = 0.0

      for (i <-0 until user_embedding.size){
        asquare += user_embedding(i) * user_embedding(i)
        bsquare += item_embedding(i) * item_embedding(i)
        abmul += user_embedding(i) * item_embedding(i)
      }
      var inner_product = 0.0
      if (asquare == 0 || bsquare == 0){
        inner_product = 0.0
      }else{
        inner_product = abmul / (Math.sqrt(asquare) * Math.sqrt(bsquare))
      }

      (row.getAs[Int]("user_id"),
        row.getAs[Int]("item_id"),
        row.getAs[Int]("category_id"),
        row.getAs[String]("content_type"),
        row.getAs[String]("timestamp"),
        row.getAs[Long]("user_item_click"),
        row.getAs[Double]("user_item_imp"),
        row.getAs[Double]("item_ctr"),
        row.getAs[Int]("is_new_user"),
        inner_product,
        row.getAs[Int]("label")
      )
    }).toDF(
      "user_id",
      "item_id",
      "category_id",
      "content_type",
      "timestamp",
      "user_item_click",
      "user_item_imp",
      "item_ctr",
      "is_new_user",
      "embedding_inner_product",
      "label")
  }

  //pre process features to generate feature vector including embedding inner product
  def preProcessInnerProductSamples(samples:DataFrame):PipelineModel = {
    val contentTypeIndexer = new StringIndexer().setInputCol("content_type").setOutputCol("content_type_index")

    val oneHotEncoder = new OneHotEncoderEstimator()
      .setInputCols(Array("content_type_index"))
      .setOutputCols(Array("content_type_vector"))
      .setDropLast(false)

    val ctr_discretizer = new QuantileDiscretizer()
      .setInputCol("item_ctr")
      .setOutputCol("ctr_bucket")
      .setNumBuckets(100)

    val vectorAsCols = Array("content_type_vector", "ctr_bucket", "user_item_click", "user_item_imp", "is_new_user", "embedding_inner_product")
    val vectorAssembler = new VectorAssembler().setInputCols(vectorAsCols).setOutputCol("vectorFeature")

    val scaler = new MinMaxScaler().setInputCol("vectorFeature").setOutputCol("scaledFeatures")

    /*
    val scaler = new StandardScaler()
      .setInputCol("vectorFeature")
      .setOutputCol("scaledFeatures")
      .setWithStd(true)
      .setWithMean(true)
    */

    val pipelineStage: Array[PipelineStage] = Array(contentTypeIndexer, oneHotEncoder, ctr_discretizer, vectorAssembler, scaler)
    val featurePipeline = new Pipeline().setStages(pipelineStage)

    featurePipeline.fit(samples)
  }


  //calculate outer product between user embedding and item embedding
  def calculateEmbeddingOuterProduct(samples:DataFrame): DataFrame ={
    import samples.sparkSession.implicits._

    samples.map(row => {
      val user_embedding = row.getAs[DenseVector]("user_embedding")
      val item_embedding = row.getAs[DenseVector]("item_embedding")

      val outerProductEmbedding:Array[Double] = Array.fill[Double](user_embedding.size)(0)

      for (i <-0 until user_embedding.size){
        outerProductEmbedding(i) = user_embedding(i) * item_embedding(i)
      }

      (row.getAs[Int]("user_id"),
        row.getAs[Int]("item_id"),
        row.getAs[Int]("category_id"),
        row.getAs[String]("content_type"),
        row.getAs[String]("timestamp"),
        row.getAs[Long]("user_item_click"),
        row.getAs[Double]("user_item_imp"),
        row.getAs[Double]("item_ctr"),
        row.getAs[Int]("is_new_user"),
        Vectors.dense(outerProductEmbedding),
        row.getAs[Int]("label")
      )
    }).toDF(
      "user_id",
      "item_id",
      "category_id",
      "content_type",
      "timestamp",
      "user_item_click",
      "user_item_imp",
      "item_ctr",
      "is_new_user",
      "embedding_outer_product",
      "label")
  }

  //pre process features to generate feature vector including embedding outer product
  def preProcessOuterProductSamples(samples:DataFrame):PipelineModel = {
    val contentTypeIndexer = new StringIndexer().setInputCol("content_type").setOutputCol("content_type_index")

    val oneHotEncoder = new OneHotEncoderEstimator()
      .setInputCols(Array("content_type_index"))
      .setOutputCols(Array("content_type_vector"))
      .setDropLast(false)

    val ctr_discretizer = new QuantileDiscretizer()
      .setInputCol("item_ctr")
      .setOutputCol("ctr_bucket")
      .setNumBuckets(100)

    val vectorAsCols = Array("content_type_vector", "ctr_bucket", "user_item_click", "user_item_imp", "is_new_user", "embedding_outer_product")
    val vectorAssembler = new VectorAssembler().setInputCols(vectorAsCols).setOutputCol("vectorFeature")

    val scaler = new MinMaxScaler().setInputCol("vectorFeature").setOutputCol("scaledFeatures")

    /*
    val scaler = new StandardScaler()
      .setInputCol("vectorFeature")
      .setOutputCol("scaledFeatures")
      .setWithStd(true)
      .setWithMean(true)
    */

    val pipelineStage: Array[PipelineStage] = Array(contentTypeIndexer, oneHotEncoder, ctr_discretizer, vectorAssembler, scaler)
    val featurePipeline = new Pipeline().setStages(pipelineStage)

    featurePipeline.fit(samples)
  }

  //normal pre process samples to generate feature vector
  def preProcessSamples(samples:DataFrame):PipelineModel = {
    val contentTypeIndexer = new StringIndexer().setInputCol("content_type").setOutputCol("content_type_index")

    val oneHotEncoder = new OneHotEncoderEstimator()
      .setInputCols(Array("content_type_index"))
      .setOutputCols(Array("content_type_vector"))
      .setDropLast(false)

    val ctr_discretizer = new QuantileDiscretizer()
      .setInputCol("item_ctr")
      .setOutputCol("ctr_bucket")
      .setNumBuckets(100)

    val vectorAsCols = Array("content_type_vector", "ctr_bucket", "user_item_click", "user_item_imp", "is_new_user", "user_embedding", "item_embedding")
    val vectorAssembler = new VectorAssembler().setInputCols(vectorAsCols).setOutputCol("vectorFeature")

    val scaler = new MinMaxScaler().setInputCol("vectorFeature").setOutputCol("scaledFeatures")

    /*
    val scaler = new StandardScaler()
      .setInputCol("vectorFeature")
      .setOutputCol("scaledFeatures")
      .setWithStd(true)
      .setWithMean(true)
    */

    val pipelineStage: Array[PipelineStage] = Array(contentTypeIndexer, oneHotEncoder, ctr_discretizer, vectorAssembler, scaler)
    val featurePipeline = new Pipeline().setStages(pipelineStage)

    featurePipeline.fit(samples)
  }
}
