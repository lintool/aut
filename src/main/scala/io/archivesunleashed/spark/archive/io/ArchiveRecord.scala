/*
 * Archives Unleashed Toolkit (AUT):
 * An open-source platform for analyzing web archives.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.archivesunleashed.spark.archive.io

import java.text.SimpleDateFormat

import org.apache.spark.SerializableWritable
import org.archive.io.arc.ARCRecord
import org.archive.io.warc.WARCRecord

import org.archive.util.ArchiveUtils
import io.archivesunleashed.data.{ArcRecordUtils, WarcRecordUtils}
import io.archivesunleashed.io.ArchiveRecordWritable
import io.archivesunleashed.io.ArchiveRecordWritable.ArchiveFormat
import io.archivesunleashed.spark.matchbox.ExtractDate.DateComponent
import io.archivesunleashed.spark.matchbox.{RemoveHttpHeader, ExtractDate, ExtractDomain}

object ArchiveRecord {
  val ISO8601 = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssX")
}

trait ArchiveRecord extends Serializable {
  def getCrawlDate: String

  def getCrawlMonth: String

  def getContentBytes: Array[Byte]

  def getContentString: String

  def getMimeType: String

  def getUrl: String

  def getDomain: String

  def getImageBytes: Array[Byte]
}

class ArchiveRecordImpl(r: SerializableWritable[ArchiveRecordWritable]) extends ArchiveRecord {
  import ArchiveRecord._

  var arcRecord: ARCRecord = null
  var warcRecord: WARCRecord = null

  if (r.t.getFormat == ArchiveFormat.ARC)
    arcRecord = r.t.getRecord.asInstanceOf[ARCRecord]
  else if (r.t.getFormat == ArchiveFormat.WARC)
    warcRecord = r.t.getRecord.asInstanceOf[WARCRecord]

  val getCrawlDate: String = {
    if (r.t.getFormat == ArchiveFormat.ARC) {
      ExtractDate(arcRecord.getMetaData.getDate, DateComponent.YYYYMMDD)
    } else {
      ExtractDate(ArchiveUtils.get14DigitDate(ISO8601.parse(warcRecord.getHeader.getDate)), DateComponent.YYYYMMDD)
    }
  }

  val getCrawlMonth: String = {
    if (r.t.getFormat == ArchiveFormat.ARC) {
      ExtractDate(arcRecord.getMetaData.getDate, DateComponent.YYYYMM)
    } else {
      ExtractDate(ArchiveUtils.get14DigitDate(ISO8601.parse(warcRecord.getHeader.getDate)), DateComponent.YYYYMM)
    }
  }

  val getContentBytes: Array[Byte] = {
    if (r.t.getFormat == ArchiveFormat.ARC) {
      ArcRecordUtils.getBodyContent(arcRecord)
    } else {
      WarcRecordUtils.getContent(warcRecord)
    }
  }

  val getContentString: String = new String(getContentBytes)

  val getMimeType: String = {
    if (r.t.getFormat == ArchiveFormat.ARC) {
      arcRecord.getMetaData.getMimetype
    } else {
      WarcRecordUtils.getWarcResponseMimeType(getContentBytes)
    }
  }

  val getUrl: String = {
    if (r.t.getFormat == ArchiveFormat.ARC) {
      arcRecord.getMetaData.getUrl
    } else {
      warcRecord.getHeader.getUrl
    }
  }

  val getDomain: String = ExtractDomain(getUrl)

  val getImageBytes: Array[Byte] = {
    if (getContentString.startsWith("HTTP/"))
      getContentBytes.slice(
        getContentString.indexOf(RemoveHttpHeader.headerEnd)
          + RemoveHttpHeader.headerEnd.length, getContentBytes.length)
    else
      getContentBytes
  }
}
