import html2canvas from 'html2canvas';
import { jsPDF } from 'jspdf';

/**
 * Captures an HTML element and downloads it as a high-resolution PNG image.
 */
export async function exportDashboardAsPng(elementId, fileName = 'Dashboard_Export') {
  const element = document.getElementById(elementId);
  if (!element) {
    throw new Error(`Element with id '${elementId}' not found.`);
  }

  // High scale for crisp retina display output
  const canvas = await html2canvas(element, {
    scale: 2,
    useCORS: true,
    allowTaint: true,
    backgroundColor: '#0b0f19',
    logging: false,
    windowWidth: element.scrollWidth,
    windowHeight: element.scrollHeight,
  });

  const imgData = canvas.toDataURL('image/png');
  const link = document.createElement('a');
  link.download = `${fileName.replace(/\.[^/.]+$/, '')}.png`;
  link.href = imgData;
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
}

/**
 * Captures an HTML element and downloads it as a professional executive A4 PDF report.
 */
export async function exportDashboardAsPdf(elementId, fileName = 'Executive_Dashboard_Report') {
  const element = document.getElementById(elementId);
  if (!element) {
    throw new Error(`Element with id '${elementId}' not found.`);
  }

  const canvas = await html2canvas(element, {
    scale: 2,
    useCORS: true,
    allowTaint: true,
    backgroundColor: '#0b0f19',
    logging: false,
    windowWidth: element.scrollWidth,
    windowHeight: element.scrollHeight,
  });

  const imgData = canvas.toDataURL('image/png');

  // A4 dimensions in mm: 297 x 210 for landscape
  const pdf = new jsPDF({
    orientation: 'landscape',
    unit: 'mm',
    format: 'a4',
  });

  const pageWidth = 297;
  const pageHeight = 210;
  const margin = 10;
  const usableWidth = pageWidth - margin * 2;
  const usableHeight = pageHeight - margin * 2;

  const imgWidth = usableWidth;
  const imgHeight = (canvas.height * usableWidth) / canvas.width;

  if (imgHeight <= usableHeight) {
    // Fits on single landscape page
    pdf.addImage(imgData, 'PNG', margin, margin, imgWidth, imgHeight);
  } else {
    // Multi-page slicing if dashboard is long
    let position = margin;
    let heightLeft = imgHeight;

    pdf.addImage(imgData, 'PNG', margin, position, imgWidth, imgHeight);
    heightLeft -= usableHeight;

    while (heightLeft > 0) {
      pdf.addPage();
      position = -(usableHeight - position);
      pdf.addImage(imgData, 'PNG', margin, position, imgWidth, imgHeight);
      heightLeft -= usableHeight;
    }
  }

  pdf.save(`${fileName.replace(/\.[^/.]+$/, '')}.pdf`);
}
