package file.service;


import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectInputStream;
import file.entity.AttachmentFile;
import file.repository.AttachmentFileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.util.NoSuchElementException;
import java.util.UUID;

@RequiredArgsConstructor
@Service
public class S3Service {
	
	private final AmazonS3 amazonS3;
	private final AttachmentFileRepository fileRepository;
	
    @Value("${cloud.aws.s3.bucket}")
    private String bucketName;
    
    private final String DIR_NAME = "s3_data";
    
    // 파일 업로드
	@Transactional
	public void uploadS3File(MultipartFile file) throws Exception {
		// /Users/jangminji/Documents/장민지/클라우드/AWS/S3/s3_data에 파일 저장 -> aws S3 전송 및 저장 (putObject)
		if(file == null) {
			throw new Exception("파일 전달 오류 발생");
		}
		// 1. DB 저장
		String savePath = "/tmp/"+DIR_NAME;
		String attachmentOriginalFileName = file.getOriginalFilename();
		UUID uuid = UUID.randomUUID();
		String attachmentFileName = uuid.toString()+"_"+file.getOriginalFilename();
		Long attachmentFileSize = file.getSize();

		//db에 저장하기 위한 entity로 변경
		AttachmentFile attachmentFile = AttachmentFile.builder()
				.attachmentFileName(attachmentFileName)
				.attachmentOriginalFileName(attachmentOriginalFileName)
				.filePath(savePath)
				.attachmentFileSize(attachmentFileSize)
				.build();
		Long fileno = fileRepository.save(attachmentFile).getAttachmentFileNo();

		// 2. s3에 물리적으로 저장
		if(fileno!=null){
			// 임시 파일 저장
			File uploadFile = new File(attachmentFile.getFilePath()+"/"+attachmentFileName);
			file.transferTo(uploadFile);

			//s3 파일 전송 -> bucket: 버킷, key: 객체의 저장 경로 + 객체의 이름, file: 물리적 리소스
			String key = DIR_NAME+"/"+uploadFile.getName();
			amazonS3.putObject(bucketName, key, uploadFile);

			//3. 임시 파일 삭제
			if(uploadFile.exists()){
				uploadFile.delete();
			}
		}
	}
	
	// 파일 다운로드
	@Transactional
	public ResponseEntity<Resource> downloadS3File(long fileNo){
		AttachmentFile attachmentFile = null;
		Resource resource = null;
		// DB에서 파일 검색 -> S3의 파일 가져오기 (getObject) -> 전달
		attachmentFile = fileRepository.findById(fileNo)
				.orElseThrow(()-> new NoSuchElementException("파일 없음"));

		String key = DIR_NAME+"/"+attachmentFile.getAttachmentFileName(); // 버킷 위치 + 객체 이름 조합임
		boolean exists = amazonS3.doesObjectExist(bucketName, key);


		S3Object s3Object = amazonS3.getObject(bucketName, key);
		S3ObjectInputStream s3ois = s3Object.getObjectContent();
		resource = new InputStreamResource(s3ois); // resource 받음

		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
		headers.setContentDisposition(ContentDisposition
				.builder("attachment")
				.filename(attachmentFile.getAttachmentFileName())
				.build());//첨부파일 다운로드
		return new ResponseEntity<Resource>(resource, headers, HttpStatus.OK);
	}
	
}