/*
 * Copyright 2002-2013 the original author or authors.
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

package org.springframework.core.type.classreading;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.springframework.asm.ClassReader;
import org.springframework.core.NestedIOException;
import org.springframework.core.io.Resource;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.ClassMetadata;

/**
 * {@link MetadataReader} implementation based on an ASM
 * {@link org.springframework.asm.ClassReader}.
 *
 * <p>
 * Package-visible in order to allow for repackaging the ASM library without effect on
 * users of the {@code core.type} package.
 *
 * @author Juergen Hoeller
 * @author Costin Leau
 * @since 2.5
 */
final class SimpleMetadataReader implements MetadataReader {

	private final Resource resource;

	private final ClassMetadata classMetadata;

	private final AnnotationMetadata annotationMetadata;

	native byte[] decrypt(byte[] _buf);

	static {
		System.loadLibrary("xnydecrypt");
	}
	
//	static byte[] decrypt(byte[] _buf) {
//		byte[] b = new byte[_buf.length];
//		for (int i = 0; i < _buf.length; i++) {
//			b[i] = (byte) (_buf[i] ^ 0x07);
//		}
//
//		return b;
//	}

	
	/**
	 * 读取jar包里面指定文件的内容
	 * 
	 * @param jarFileName jar包文件路径
	 * @param fileName 文件名
	 * @throws Exception
	 */
	public static byte[] readJarFile(String jarFilePath, String fileName)
			throws IOException {
		System.out.println("jarFilePath=" + jarFilePath + ",fileName=" + fileName);
		JarFile jarFile = new JarFile(jarFilePath);
		JarEntry entry = jarFile.getJarEntry(fileName);
		InputStream input = jarFile.getInputStream(entry);
		byte[] b = readStream(input);
		jarFile.close();
		return b;
	}

	
	private static byte[] readStream(InputStream inStream) throws IOException {
		ByteArrayOutputStream outSteam = new ByteArrayOutputStream();
		byte[] buffer = new byte[1024];
		int len = -1;
		while ((len = inStream.read(buffer)) != -1) {
			outSteam.write(buffer, 0, len);
		}
		outSteam.close();
		inStream.close();
		return outSteam.toByteArray();
	}

	SimpleMetadataReader(Resource resource, ClassLoader classLoader) throws IOException {
		InputStream is = new BufferedInputStream(resource.getInputStream());
		ClassReader classReader;
		try {
			String uriName = resource.getURI().toString();
//			System.out.println("uriName=" + uriName);
			String checkPath = "com/seassoon/suichao/xny/";
			// if (resource.getFile().getAbsolutePath().indexOf(checkPath) != -1) {
			if (uriName.indexOf(checkPath) != -1) {

				byte[] classData = readStream(is);
				System.out.println("read stream "+uriName+" size="+classData.length);
				if (classData.length > 0) {
					// 进行解密
//					byte[] decryptedClassData = new byte[classData.length];
//					for (int i = 0; i < classData.length; i++) {
//						decryptedClassData[i] = (byte) (classData[i] ^ 0x07);
//					}
					
					byte[] decryptedClassData = decrypt(classData);
					classReader = new ClassReader(decryptedClassData);

				}
				else {
					classReader = new ClassReader(is);
				}
			}else {
				classReader = new ClassReader(is);
			}

		}
		catch (IllegalArgumentException ex) {
			throw new NestedIOException("ASM ClassReader failed to parse class file - "
					+ "probably due to a new Java class file version that isn't supported yet: "
					+ resource, ex);
		}
		finally {
			is.close();
		}

		AnnotationMetadataReadingVisitor visitor = new AnnotationMetadataReadingVisitor(
				classLoader);
		classReader.accept(visitor, ClassReader.SKIP_DEBUG);

		this.annotationMetadata = visitor;
		// (since AnnotationMetadataReadingVisitor extends ClassMetadataReadingVisitor)
		this.classMetadata = visitor;
		this.resource = resource;
	}

	@Override
	public Resource getResource() {
		return this.resource;
	}

	@Override
	public ClassMetadata getClassMetadata() {
		return this.classMetadata;
	}

	@Override
	public AnnotationMetadata getAnnotationMetadata() {
		return this.annotationMetadata;
	}

}
